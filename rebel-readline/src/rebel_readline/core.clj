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

  ;; IMPORTANT NOTE:

  ;; The rebel line reader will attempt to manipulate the terminal
  ;; that initiated the JVM process. For this reason it is important
  ;; to start your JVM in a terminal - an ansi compatible terminal.

  ;; That means launching your process using the

  ;; - the java command
  ;; - the Clojure clj tool
  ;; - lein trampoline
  ;; - boot - would need to run in boot's worker pod

  ;; Launching from a process initiated by lein will not work and
  ;; launching from a boot pod will not cut it either.

(defn line-reader
  "Creates a rebel line reader takes a service as an argument.
 
  IMPORTANT NOTE:

  This function will attempt to manipulate the terminal that initiated
  the JVM process. For this reason it is important to start your JVM
  in a terminal.

  That means launching your process using the

  - the java command
  - the Clojure clj tool
  - lein trampoline
  - boot - would need to run in boot's worker pod

  Launching from a process initiated by lein will not work and
  launching from a boot pod will not cut it either.

  The underlying Terminal manipulation code is Jline3 and it makes
  every effort to be compatible with a wide array of terminals. It is
  entirely possible that your terminal is not well supported.
  
  Example:
    (line-reader (rebel-readline.service.impl.local-clojure-service/create))"
  [service]
  {:service service
   :line-reader (lr/line-reader* service)})

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
      (let [save-out *out*]
        (when (:redirect-output (srv/config))
          (alter-var-root
           #'*out*
           (fn [_] (line-print-writer/print-writer :out (output-handler reader)))))
        (try
          (let [res' (.readLine line-reader (srv/prompt))]
            (if-not (commands/handle-command res')
              res'
              command-executed))
          (finally
            (when (:redirect-output (srv/config))
              (alter-var-root #'*out* (fn [_] save-out)))))))))

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

(defn- has-remaining?
  "Takes a PushbackReader and returns true if the next character is not negative.
   i.e not the end of the readers stream."
  [pbr]
  (let [x (.read pbr)]
    (and (not (neg? x))
         (do (.unread pbr x) true))))

(defn clj-repl-read
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
             (rebel-readline.service.impl.local-clojure-service/create))))"
  [line-reader]
  (let [reader-buffer (atom (clojure.lang.LineNumberingPushbackReader.
                             (java.io.StringReader. "")))]
    (fn [request-prompt request-exit]
      (if (has-remaining? @reader-buffer)
        (binding [*in* @reader-buffer]
          (clojure.main/repl-read request-prompt request-exit))
        (let [possible-forms (repl-read-line line-reader request-prompt request-exit)]
          (if (#{request-prompt request-exit} possible-forms)
            possible-forms
            (if-not (string/blank? possible-forms)
              (do
                (reset! reader-buffer (clojure.lang.LineNumberingPushbackReader.
                                       (java.io.StringReader. (str possible-forms "\n"))))
                (binding [*in* @reader-buffer]
                  (clojure.main/repl-read request-prompt request-exit)))
              request-prompt)))))))

(defn stream-read-line
  "This function reads lines and returns them ready to be read by a
  java.io.Reader. This basically adds newlines at the end of readline
  results.

  This function returns `nil` if it is end of the supplied readlines
  parent input stream. Such is the case when a a exit is requested.

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
   (clojure.main :prompt (fn[])))"

  [service & body]
  `(let [lr# (line-reader ~service)]
    (binding [*in* (clojure.lang.LineNumberingPushbackReader.
                    (rebel-readline.io.callback-reader/callback-reader #(stream-read-line lr#)))]
      ~@body)))

