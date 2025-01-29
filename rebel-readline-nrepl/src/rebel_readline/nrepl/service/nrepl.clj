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

(derive ::service ::clj-reader/clojure)

;; callback system
(defn add-callback! [{:keys [::state]} id f]
  (swap! state assoc-in [:id-callbacks id] f))

(defn remove-callback! [{:keys [::state]} id]
  (swap! state update :id-callbacks dissoc id))

(defn set-current-eval-id! [{:keys [::state]} id]
  (swap! state assoc :current-eval-id id))

(defn remove-current-eval-id! [{:keys [::state]}]
  (swap! state dissoc :current-eval-id))

(defn dispatch-response! [{:keys [::state] :as service} msg]
  (doseq [f (vals (get @state :id-callbacks))]
    (f msg)))

;; message callback-api
(defn new-id [] (nrepl.misc/uuid))

(defn select-key [key shouldbe k]
  (fn [msg]
    (when (= (get msg key) shouldbe)
      (k msg))))

(defn on-key [key callback k]
  (fn [msg]
    (when-let [v (get msg key)]
      (callback v))
    (k msg)))

(defn session-id [session' id' k]
  (->> k
       (select-key :id id')
       (select-key :session session')))

(defn out-err [print-out print-err k]
  (->> k
       (on-key :out print-out)
       (on-key :err print-err)))

(defn value [callback k]
  (on-key :value callback k))

(defn handle-statuses [pred callback k]
  (fn [{:keys [status] :as msg}]
    (when (some pred status)
      (callback msg))
    (k msg)))

(defn done [callback k]
  (handle-statuses #{"done" "interrupt"} callback k))

(defn error [callback k]
  (handle-statuses #{"error" "eval-error"} callback k))

(defn send-msg! [{:keys [::state] :as service} {:keys [session id] :as msg} callback]
  (assert session)
  (assert id)
  (add-callback!
   service id
   (->> callback
        (error (fn [_] (remove-callback! service id)))
        (done  (fn [_] (remove-callback! service id)))
        (session-id session id)))
  (tap> msg)
  (nrepl.transport/send (:conn @state) msg))

(defn eval-session [{:keys [::state]}]
  (get @state :session))

(defn tool-session [{:keys [::state]}]
  (get @state :tool-session))

(defn new-message [{:keys [::state] :as service} msg]
  (merge
   {:session (eval-session service)
    :id (new-id)
    :ns (:current-ns @state)}
   msg))

(defn new-tool-message [service msg]
  (new-message
   service
   (merge {:session (tool-session service)}
          msg)))

(defn eval-code [{:keys [::state] :as service} code-str k]
  (let [{:keys [id] :as message}
        (new-message service {:op "eval" :code code-str})
        prom (promise)
        finish (fn [_]
                 (deliver prom ::done)
                 (remove-current-eval-id! service))]
    (set-current-eval-id! service id)
    (send-msg! service
               message
               (->> k
                    (on-key :ns #(swap! state assoc :current-ns %))
                    (done finish)
                    (error finish)))
    @prom))

(defn interrupt [{:keys [::state] :as service}]
  ;; TODO having a timeout and then calling the
  ;; callback with a done message could prevent
  ;; terminal lockup in extreme cases
  (let [{:keys [current-eval-id]} @state]
    (when current-eval-id
      (send-msg!
       service
       (new-message service {:op "interrupt" :interrupt-id current-eval-id})
       identity))))

(defn lookup [{:keys [::state] :as service} symbol]
  (let [prom (promise)]
    (send-msg! service
               (new-tool-message service {:op "lookup" :sym symbol})
               (->> identity
                    (done #(deliver prom
                                    (some-> %
                                            :info
                                            not-empty
                                            (update :arglists clojure.edn/read-string))))))
    (deref prom 400 nil)))

(defn completions [{:keys [::state] :as service } prefix]
    (let [prom (promise)]
      (send-msg! service
                 (new-tool-message service {:op "completions" :prefix prefix})
                 (->> identity
                      (done #(deliver prom (get % :completions)))))
      (deref prom 400 nil)))

(defn tool-eval-code [service code-str]
  (let [prom (promise)]
    (send-msg! service
               (new-tool-message service {:op "eval" :code code-str})
               (->> identity
                    (value #(deliver prom %))))
    (deref prom 400 nil)))

(defmethod clj-reader/-resolve-meta ::service [self var-str]
  (lookup self var-str))

(defmethod clj-reader/-current-ns ::service [{:keys [::state] :as self}]
  (:current-ns @state))

(defmethod clj-reader/-source ::service [self var-str]
  (some->> (pr-str `(clojure.repl/source-fn (symbol ~var-str)))
           (tool-eval-code self)
           read-string
           (hash-map :source)))

(defmethod clj-reader/-apropos ::service [self var-str]
  (some->> (pr-str `(clojure.repl/apropos ~var-str))
           (tool-eval-code self)
           read-string))

(defmethod clj-reader/-complete ::service [self word options]
  (some->> (completions self word)
           (map #(update % :type keyword))))

(defmethod clj-reader/-doc ::service [self var-str]
  (when-let [{:keys [ns name arglists doc]} (lookup self var-str)]
    (when doc
      (let [url (clj-utils/url-for (str ns) (str name))]
        (cond-> {:doc (str ns "/" name "\n"
                           arglists "\n  " doc)}
          url (assoc :url url))))))

(defmethod clj-reader/-eval ::service [self form]
  (let [res (atom {})
        prom (promise)]
    (send-msg!
     self
     (new-tool-message self {:op "eval" :code (pr-str form)})
     (->> identity
          (out-err #(swap! res update :out str %)
                   #(swap! res update :err str %))
          (value (fn [x] (deliver prom (assoc @res :printed-result x))))))
    (deref prom 5000 :timed-out)))

(defmethod clj-reader/-read-string ::service [self form-str]
  (service-local/default-read-string form-str))

(defn poll-for-responses [{:keys [::state] :as options} conn]
  (loop []
    (when (:response-poller @state)
      (let [continue
            (try
              (when-let [{:keys [id out err value ns session] :as resp}
                         (nrepl.transport/recv conn 100)]
                (tap> resp)
                (dispatch-response! options resp))
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
  ([{:keys [host port tls-keys-file] :as options}]
   (let [conn (nrepl/connect (cond-> {:port port}
                               host (assoc :host host)
                               tls-keys-file (assoc :tls-keys-file tls-keys-file))) ;; TODO fix this
         client (nrepl/client conn Long/MAX_VALUE)
         session (nrepl/new-session client)
         tool-session (nrepl/new-session client)
         options (merge
                  clj-reader/default-config
                  (tools/user-config)
                  options
                  {:rebel-readline.service/type ::service
                   ::state (atom {:conn conn
                                  :current-ns "user"
                                  :client client
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
    (start-polling service)
    #_(swap! (::state service) assoc :command-id (nrepl.misc/uuid))
    (try
      (let [res (eval-code service "(ns heythere)" identity)]
        
        res
        (::state service)
        )
      (finally
        (stop-polling service))))


#_(add-tap (fn [x] (spit "DEBUG.log" (prn-str x)  :append true)))
