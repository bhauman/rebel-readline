(ns rebel-readline.core
  (:refer-clojure :exclude [read-line])
  (:require
   [clojure.string :as string]
   [rebel-readline.commands :as commands]
   [rebel-readline.io.callback-reader]
   [rebel-readline.io.line-print-writer :as line-print-writer]
   [rebel-readline.jline-api :as api]
   [rebel-readline.tools :as tools]
   [rebel-readline.tools.syntax-highlight :as highlight])
  (:import
   [org.jline.reader
    UserInterruptException
    EndOfFileException]))

(defmacro ensure-terminal
  "Bind the rebel-readline.jline-api/*terminal* var to a new Jline
  terminal if needed, otherwise use the currently bound one.

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
  [& body]
  `(binding [rebel-readline.jline-api/*terminal*
             (or rebel-readline.jline-api/*terminal* (rebel-readline.jline-api/create-terminal))]
     ~@body))

(defmacro with-line-reader [line-reader & body]
  `(ensure-terminal
    (binding [rebel-readline.jline-api/*line-reader* ~line-reader]
      ~@body)))

(defn help-message []
  "[Rebel readline] Type :repl/help for online help info")

(defn output-handler
  "Creates a function that takes output to be redirected \"above\" a
  running readline editor."
  [line-reader]
  (fn [{:keys [text]}]
    (when (not (string/blank? text))
      (binding [api/*line-reader* line-reader]
        (api/reader-println text)))))

(defn read-line
  "Reads a line from the currently rebel line reader. If you supply the
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
  [& [command-executed]]
  (let [command-executed (or command-executed "")]
    (let [redirect-output? (:redirect-output @api/*line-reader*)
          save-out (volatile! *out*)
          redirect-print-writer
          (line-print-writer/print-writer :out (output-handler api/*line-reader*))]
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
          ;; this is intensely disatisfying
          ;; but we are blocking redisplays while the
          ;; readline is initially drawn
          (api/block-redisplay-millis 100)
          (let [res' (.readLine api/*line-reader* (tools/prompt))]
            (if-not (commands/handle-command res')
              res'
              command-executed)))
        (finally
          (when redirect-output?
            (alter-var-root #'*out* (fn [_] @save-out))))))))

(defn repl-read-line
  "A readline function that converts the Exceptions normally thrown by
  org.jline.reader.impl.LineREaderImpl that signal user interrupt or
  the end of the parent stream into concrete sentinal objects that one
  can act on.

  This follows the pattern established by `clojure.main/repl-read`

  This function either returns the string read by this readline or the
  request-exit or request-prompt sentinal objects."
  [request-prompt request-exit]
  (try
    (read-line request-prompt)
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
  (fn []
    (let [reader-buffer (atom (create-buffered-reader-fn ""))]
      (fn [request-prompt request-exit]
        (if (has-remaining-pred @reader-buffer)
          (binding [*in* @reader-buffer]
            (repl-read-fn request-prompt request-exit))
          (let [possible-forms (repl-read-line request-prompt request-exit)]
            (if (#{request-prompt request-exit} possible-forms)
              possible-forms
              (if-not (string/blank? possible-forms)
                (do
                  (reset! reader-buffer (create-buffered-reader-fn (str possible-forms "\n")))
                  (binding [*in* @reader-buffer]
                    (repl-read-fn request-prompt request-exit)))
                request-prompt))))))))

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

  (with-readline-input-stream (rebel-readline.clojure.service.local/create)
   (clojure.main/repl :prompt (fn[])))"
  [service & body]
  `(let [lr# (line-reader ~service)]
     (binding [*in* (clojure.lang.LineNumberingPushbackReader.
                     (rebel-readline.io.callback-reader/callback-reader
                      #(stream-read-line lr#)))
               rebel-readline.jline-api/*line-reader* lr#]
       ~@body)))
