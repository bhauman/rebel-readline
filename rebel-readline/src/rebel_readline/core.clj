(ns rebel-readline.core
  (:refer-clojure :exclude [read-line])
  (:require
   [clojure.string :as string]
   [rebel-readline.service.core :as srv]
   [rebel-readline.jline-api :as api]
   [rebel-readline.io.line-print-writer :as line-print-writer]
   [rebel-readline.io.callback-reader]
   [rebel-readline.commands :as commands]
   [rebel-readline.line-reader :as lr])
  (:import
   [org.jline.reader
    UserInterruptException
    EndOfFileException]))

(defn help-message []
  "[Rebel readline] Type :repl/help for online help info")

(defn line-reader
  "Creates a line reader takes a service as an argument.

  A service implements the protocols found in `rebel-readline.service.core`

  Example:
    (line-reader (rebel-readline.service.impl.local-clojure-service/create))

  This function also takes an optional options map.

  The available options are:

  :terminal - to give the line reader and existing terminal
  :completer - to override the clojure based completer
  :highlighter - to override the clojure based systax highlighter
  :parser - to override the clojure base word parser
  :assert-system-terminal - wether to throw an exception when we can't
                            connect to a system terminal

  --------------------------------------------------------------------
  IMPORTANT NOTE:
  --------------------------------------------------------------------

  This function will attempt to manipulate the terminal that initiated
  the JVM process. For this reason it is important to start your JVM
  in a terminal.

  That means launching your process using the

  - the java command
  - the Clojure `clojure` tool
  - lein trampoline
  - boot - would need to run in boot's worker pod

  Launching from a process initiated by lein will not work and
  launching from a boot pod will not cut it either.

  The underlying Terminal manipulation code is Jline3 and it makes
  every effort to be compatible with a wide array of terminals. It is
  entirely possible that your terminal is not well supported."
  [service & [options]]
  {:service service
   :line-reader (lr/line-reader* service options)})

(defn- output-handler
  "Creates a function that takes output to be redirected \"above\" a
  running readline editor."
  [{:keys [line-reader service]}]
  (fn [{:keys [text]}]
    (when (not (string/blank? text))
      (binding [srv/*service* service]
        (api/reader-println line-reader text)))))

(defn read-line
  "Reads a line from the supplied rebel line reader. If you supply the
  optional `command-executed` sentinal value, it will be returned when
  a repl command is executed, otherwise a blank string will be
  returned when a repl command is executed.

  This function activates the rebel line reader which, in turn, will put
  the terminal that launched the jvm process into \"raw mode\" during the
  readline operation.

  You can think of the readline opertaion as a launching of an editor
  for the breif period that the line is read.

  If :redirect-output is truthy (the default value) in the supplied
  rebel line reader service config this function will alter the root
  binding of the *out* var to prevent extraneous output from
  corrupting the read line editors output.

  Once the reading is done it returns the terminal to its original
  settings."
  [{:keys [service line-reader] :as reader} & [command-executed]]
  (let [command-executed (or command-executed "")]
    (binding [srv/*service* service
              api/*line-reader* line-reader]
      ;; TODO consider redirecting *err* as well
      (let [redirect-output? (:redirect-output (srv/config))
            save-out (volatile! *out*)
            redirect-print-writer
            (line-print-writer/print-writer :out (output-handler reader))]
        (.flush *out*)
        (.flush *err*)
        (when redirect-output?
          (alter-var-root
           #'*out*
           (fn [root-out]
             (vreset! save-out root-out)
             redirect-print-writer)))
        (try
          (binding [*out* redirect-print-writer]
            (let [res' (.readLine line-reader (srv/prompt))]
              (if-not (commands/handle-command res')
                res'
                command-executed)))
          (finally
            (when redirect-output?
              (alter-var-root #'*out* (fn [_] @save-out)))))))))

(defn repl-read-line
  "A readline function that converts the Exceptions normally thrown by
  org.jline.reader.impl.LineREaderImpl that signal user interrupt or
  the end of the parent stream into concrete sentinal objects that one
  can act on.

  This follows the pattern established by `clojure.main/repl-read`

  This function either returns the string read by this readline or the
  request-exit or request-prompt sentinal objects."
  [{:keys [service line-reader] :as reader} request-prompt request-exit]
  (try
    (read-line reader request-prompt)
    (catch UserInterruptException e
      request-prompt)
    (catch EndOfFileException e
      request-exit)))

(defn has-remaining?
  "Takes a PushbackReader and returns true if the next character is not negative.
   i.e not the end of the readers stream."
  [pbr]
  (let [x (.read pbr)]
    (and (not (neg? x))
         (do (.unread pbr x) true))))

(defn create-buffered-repl-reader-fn [create-buffered-reader-fn has-remaining-pred repl-read-fn]
  (fn [line-reader]
    (let [reader-buffer (atom (create-buffered-reader-fn ""))]
      (fn [request-prompt request-exit]
        (if (has-remaining-pred @reader-buffer)
          (binding [*in* @reader-buffer]
            (repl-read-fn request-prompt request-exit))
          (let [possible-forms (repl-read-line line-reader request-prompt request-exit)]
            (if (#{request-prompt request-exit} possible-forms)
              possible-forms
              (if-not (string/blank? possible-forms)
                (do
                  (reset! reader-buffer (create-buffered-reader-fn (str possible-forms "\n")))
                  (binding [*in* @reader-buffer]
                    (repl-read-fn request-prompt request-exit)))
                request-prompt))))))))

(def clj-repl-read
  "A drop in replacement for clojure.main/repl-read, since a readline
  can return multiple Clojure forms this function is stateful and
  buffers the forms and returns the next form on subsequent reads.

  This function is a constructor that takes a line-reader and returns
  a function that can replace `clojure.main/repl-read`.

  Example Usage:

  (clojure.main/repl
   :prompt (fn []) ;; prompt is handled by line-reader
   :read (clj-repl-read
           (line-reader
             (rebel-readline.service.impl.local-clojure-service/create))))

  Or catch a bad terminal error and fall back to clojure.main/repl-read:

  (clojure.main/repl
   :prompt (fn [])
   :read (try
          (clj-repl-read
           (line-reader
             (rebel-readline.service.impl.local-clojure-service/create)))
          (catch clojure.lang.ExceptionInfo e
             (if (-> e ex-data :type (= :rebel-readline.line-reader/bad-terminal))
                (do (println (.getMessage e))
                    clojure.main/repl-read)
                (throw e)))))"
  (create-buffered-repl-reader-fn
   (fn [s] (clojure.lang.LineNumberingPushbackReader.
            (java.io.StringReader. s)))
   has-remaining?
   clojure.main/repl-read))

(defn stream-read-line
  "This function reads lines and returns them ready to be read by a
  java.io.Reader. This basically adds newlines at the end of readline
  results.

  This function returns `nil` if it is end of the supplied readlines
  parent input stream or if a process exit is requested.

  This function was designed to be supplied to a `rebel-readline.io.calback-reader`

  Example:

  ;; this will create an input stream to be read from by a Clojure/Script REPL

  (rebel-readline.io.calback-reader/callback-reader #(stream-read-line line-reader))"
  [{:keys [service line-reader] :as reader}]
  (let [request-prompt (Object.)
        request-exit   (Object.)
        possible-result (repl-read-line reader request-prompt request-exit)]
    (cond
      (= request-prompt possible-result) (System/getProperty "line.separator")
      (= request-exit possible-result) nil
      :else (str possible-result (System/getProperty "line.separator")))))

(defmacro with-readline-input-stream
  "This macro takes a rebel readline service and binds *in* to an a
  `clojure.lang.LineNumberingPushbackReader` that is backed by the
  readline.

  This is perhaps the easiest way to utilize this readline library.

  The downside to using this method is if you are working on something
  that reads from the *in* that wouldn't benefit from the features of
  this readline lib. In that case I would look at `clj-repl-read` where
  the readline is only engaged during the read portion of the REPL.

  Examples:

  (with-readline-input-stream (rebel-readline.service.impl.local-clojure-service/create)
   (clojure.main/repl :prompt (fn[])))"
  [service & body]
  `(let [lr# (line-reader ~service)]
    (binding [*in* (clojure.lang.LineNumberingPushbackReader.
                    (rebel-readline.io.callback-reader/callback-reader #(stream-read-line lr#)))]
      ~@body)))
