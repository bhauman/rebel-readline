(ns rebel-readline.nrepl.service.nrepl
  (:require
   [rebel-readline.clojure.line-reader :as clj-reader]
   [rebel-readline.tools :as tools]
   [rebel-readline.clojure.service.local :as service-local]
   [rebel-readline.jline-api.attributed-string :as as]
   [rebel-readline.clojure.utils :as clj-utils]   
   [clojure.string :as string]
   [nrepl.core :as nrepl]
   [nrepl.misc :as nrepl.misc])
  (:import
   [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn log [x]
  (spit "./mytempdebug.log" (prn-str x) :append true)
  x)

(def default-timeout 3000)

(derive ::service ::clj-reader/clojure)

(defn- end-of-stream? [client options command-id message]
  (let [relevant-message (or (= command-id (:id message)) (:global message))
        error (some #{"error" "eval-error"} (:status message))
        done (some #{"done" "interrupted"} (:status
                                            message))]
    #_(when error
      (let [caught (:caught options)]
        (when (or (symbol? caught) (list? caught))
          (execute-with-client client options (str "(" (pr-str caught) ")"))))
      (when (:global message)
        (throw (:error message))))

    (and relevant-message (or error done))))

(defn session-responses [{:keys [::state] :as service} session]
  (lazy-seq
   (cons (.poll ^LinkedBlockingQueue ((:response-queues @state) session)
                50
                TimeUnit/MILLISECONDS)
         (session-responses service session))))

(defn send-message [{:keys [::state] :as service} {:keys [session id] :as message-to-send}]
  (let [session (or (:session message-to-send) (:session @state))
        client (:client @state)
        session-sender (nrepl/client-session client :session session)]
    (session-sender message-to-send)
    (session-responses service session)))

(defn execute-with-client* [{:keys [::state] :as service} options form]
  (let [command-id (nrepl.misc/uuid)
        client (:client @state)
        message-to-send
        (merge (get-in options [:nrepl-context :interactive-eval])
               {:op "eval" :code form :id command-id})]
    (swap! state assoc :current-command-id command-id)
    (let [res (doall
               (take-while
                #(not (end-of-stream? client message-to-send command-id %))
                (send-message service message-to-send)))]
      (swap! state dissoc :current-command-id)
      res)))

(defn execute-with-client [{:keys [::state] :as service} options form]
  (doseq [{:keys [ns value out err] :as res}
          (execute-with-client* service options form)]
    #_(when (some #{"need-input"} (:status res))
        (reset! current-command-id nil)
        (let [input-result (read-input-line-fn)
              in-message-id (nrepl.misc/uuid)
              message {:op "stdin" :stdin (when input-result
                                            (str input-result "\n"))
                       :id in-message-id}]
          (session-sender message)
          (reset! current-command-id command-id)))
    (when value ((:print-value options) value))))

;; don't print anything out
(defn execute-with-client-no-output [{:keys [::state] :as service} options form]
  (swap! state assoc :prn-output? false)
  (let [res (execute-with-client* service options form)]
    (swap! state assoc :prn-output? true)
    res))

;; this is bogus
(defn evaluate [{:keys [::state] :as service} form]
  (let [results (atom "nil")]
    (execute-with-client
     service
     {:print-value (partial reset! results)
      :session (:session @state)}
     form)
    @results))

(defn tool-evaluate [{:keys [::state] :as service} form]
  (let [results (atom "nil")]
    (execute-with-client ;; -no-output  TODO
     service
     {:print-value (partial reset! results)
      :session (:tool-session @state)}
     form)
    @results))

(defn lookup [service symbol]
  ;; TODO used a cached ns to look this up on the tool session?
  (->> (send-message service {:op "lookup" :sym symbol})
       first))

(defn completions [service prefx]
  ;; TODO used a cached ns to look this up on the tool session?
  (->> (send-message service {:op "completions" :prefix prefx})
       first))





#_(let [var-str "range"]
    (pr-str `(or (try
                   (clojure.core/some->
                    ~var-str
                    clojure.core/symbol
                    clojure.core/resolve
                    clojure.core/meta)
                   (catch Throwable ~'x nil))
                 #_(some->
                    ~var-str
                    clojure.core/symbol
                    clojure.core/find-ns
                    meta
                    (clojure.core/assoc :ns ~var-str)))))

(defmethod clj-reader/-resolve-meta ::service [self var-str]
  (get (lookup self var-str) :info))

(defmethod clj-reader/-current-ns ::service [{:keys [::state] :as self}]
  (->> (pr-str `(clojure.core/some-> *ns* str symbol))
       (evaluate self)))

(defmethod clj-reader/-source ::service [self var-str]
  (some->> (pr-str `(clojure.repl/source-fn (symbol ~var-str)))
           (tool-evaluate self)
           read-string
           (hash-map :source)))

(defmethod clj-reader/-apropos ::service [self var-str]
  (some->> (pr-str `(clojure.repl/apropos ~var-str))
           (evaluate self)
           read-string))

(defmethod clj-reader/-complete ::service [self word options]
  (some->> (completions self word)
           :completions
           (map #(update % :type keyword))))

(defmethod clj-reader/-doc ::service [self var-str]
  (when-let [{:keys [ns name arglists doc]} (-> (:info (lookup self var-str)))]
    (when doc
      (let [url (clj-utils/url-for (str ns) (str name))]
        (cond-> {:doc (str ns "/" name "\n"
                           arglists "\n  " doc)}
          url (assoc :url url))))))

(defmethod clj-reader/-eval ::service [self form]
  (let [res (->> (execute-with-client-no-output self {} (pr-str form))
                 nrepl/combine-responses)]
    (assoc res
           :printed-result (first (:value res)))))

(defmethod clj-reader/-read-string ::service [self form-str]
  (service-local/default-read-string form-str))

;; perhaps add calback
(defn poll-for-responses [{:keys [::state] :as options} conn]
  (loop []
    (when (:response-poller @state)
      (let [
            continue
            (try
              (when-let [{:keys [out err] :as resp}
                         (nrepl.transport/recv conn 100)]
                (let [print-err-fn (get @state :print-err-fn print)
                      print-out-fn (get @state :print-out-fn print)
                      prn-output?  (get @state :prn-output? true)]
                  (if-not prn-output?
                    (.offer ^LinkedBlockingQueue ((:response-queues @state)
                                                  (:session resp))
                            resp)
                    (do
                      (when err (print-err-fn err))
                      (when out (print-out-fn out))
                      (when-not (or err out)
                        (.offer ^LinkedBlockingQueue ((:response-queues @state)
                                                      (:session resp))
                                resp))
                      (flush)))))
              :success
              #_(catch Throwable t
                  #_(notify-all-queues-of-error t)
                  #_(when (System/getenv "DEBUG") (clojure.repl/pst t))
                  #_:failure))]
        (when (= :success continue)
          (recur))))))

(defn start-polling [{:keys [::state]}]
  (let [response-poller (Thread. ^Runnable (:runnable-poller @state))]
    (swap! state assoc :response-poller response-poller)
    (doto ^Thread response-poller
      (.setName "Rebel Readline nREPL response poller")
      (.setDaemon true)
      (.start))))

(defn stop-polling [{:keys [::state]}]
  (swap! state dissoc :response-poller))

(defn create
  ([] (create nil))
  ([options]
   (let [conn (nrepl/connect :port 49319) ;; TODO fix this
         client (nrepl/client conn Long/MAX_VALUE)
         session (nrepl/new-session client)
         tool-session (nrepl/new-session client)
         options (merge
                  {:eval-timeout default-timeout}
                  clj-reader/default-config
                  (tools/user-config)
                  options
                  {:rebel-readline.service/type ::service
                   ::state (atom {:conn conn
                                  :current-ns "user"
                                  :client client
                                  :response-queues {session (LinkedBlockingQueue.)
                                                    tool-session (LinkedBlockingQueue.)}
                                  :current-command-id nil
                                  :session session
                                  :tool-session tool-session
                                  ; :print-err-fn (bound-fn [s] (print (as/astr [s (tools/color :widget/error)])))
                                  })})]
     (swap! (::state options)
            assoc
            :runnable-poller
            (bound-fn [] (poll-for-responses options conn)))
     options)))



#_(let [out *out*
        service (create )]
    service
    (start-polling service)
    (let [res (clj-reader/-complete service
                      "ma"
                      {}
                      #_"(doto (Thread. ^Runnable (fn [] (Thread/sleep 2000) (println \"did it\") (flush)))
(.setDaemon true)
(.start))"
                        )]
      (stop-polling service)
      res))
