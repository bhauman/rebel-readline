(ns rebel-readline.core
  (:refer-clojure :exclude [read-line])
  (:require
   [clojure.string :as string]
   [rebel-readline.commands :as commands]
   [rebel-readline.io.callback-reader]
   [rebel-readline.jline-api :as api]
   [rebel-readline.tools :as tools]
   [rebel-readline.utils :as utils])
  (:import
   [org.jline.reader
    UserInterruptException
    EndOfFileException]))

(defmacro ensure-terminal
  "Bind the rebel-readline.jline-api/*terminal* var to a new Jline
  terminal if needed, otherwise use the currently bound one.

  Will throw a clojure.lang.ExceptionInfo with a data payload of
  `{:type :rebel-readline.jline-api/bad-terminal}` if JVM wasn't
  launched from a terminal process.

  There should really only be one instance of a Jline terminal as it
  represents a \"connection\" to the terminal that launched JVM
  process.

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

(defmacro with-line-reader
  "This macro take a line-reader and binds it.  It is one of the
  primary ways to utilize this library. You can think of the
  rebel-readline.jline-api/*line-reader* binding as an alternative in
  source that the rebel-readline.core/read-line function reads from.

  Example:
  (require '[rebel-readline.core :as rebel])

  (rebel/with-line-reader
    (rebel-readline.clojure.line-reader/create
      (rebel-readline.clojure.service.local/create))
    ;; optionally bind the output directly to the jline terminal
    ;; in such a way so that output won't corrupt the terminal
    ;; this is optional
    (binding [*out* (rebel-readline.jline-api/safe-terminal-writer)]
      (clojure.main/repl
        ;; this will create a fn that reads from the *line-reader*
        :read (rebel-readline.clojure.main/create-repl-read)
       :prompt (fn []))))"
  [line-reader & body]
  `(ensure-terminal
    (binding [rebel-readline.jline-api/*line-reader* ~line-reader]
      ~@body)))

(defn help-message
  "Returns a help message to print before enguaging the
  readline. Helpful for repl development."
  []
  "[Rebel readline] Type :repl/help for online help info")

(defn read-line-opts
  "Like read-line, but allows overriding of the LineReader prompt, buffer, and mask parameters.
   
   :prompt 
     Allows overriding with a cusom prompt
   :buffer
     The default value presented to the user to edit, may be null.
   :mask 
     Should be set to a single character used by jline to bit-mask.  
     Characters will not be echoed if they mask to 0
     Might do crazy stuff with rebel-readline, use with caution.
     defaults to nil (no mask)
   :command-executed
     sentinal value to be returned when a repl command is executed, otherwise a 
     blank string will be returned when a repl command is executed.
  "
  [ & {prompt :prompt
       mask :mask
       buffer :buffer 
       command-executed :command-executed
       :or {prompt nil buffer nil mask nil command-executed ""}}]
  
  (let [redirect-output? (:redirect-output @api/*line-reader*)
        save-out (volatile! *out*)
        redirect-print-writer (api/safe-terminal-writer api/*line-reader*)]
    (when redirect-output?
      (alter-var-root
       #'*out*
       (fn [root-out]
         (vreset! save-out root-out)
         redirect-print-writer)))
    (try
      (binding [*out* redirect-print-writer]
        ;; this is intensely disatisfying
        ;; but we are blocking concurrent redisplays while the
        ;; readline prompt is initially drawn
        (api/block-redisplay-millis 100)
        (let [res' (.readLine api/*line-reader* (or prompt (tools/prompt)) mask buffer)]
          (if-not (commands/handle-command res')
            res'
            command-executed)))
      (finally
        (when redirect-output?
          (flush)
          (alter-var-root #'*out* (fn [_] @save-out)))))))

(defn read-line
  "Reads a line from the currently bound
  rebel-readline.jline-api/*line-reader*. If you supply the optional
  `command-executed` sentinal value, it will be returned when a repl
  command is executed, otherwise a blank string will be returned when
  a repl command is executed.

  This function activates the rebel line reader which, in turn, will put
  the terminal that launched the jvm process into \"raw mode\" during the
  readline operation.

  You can think of the readline operation as a launching of an editor
  for the brief period that the line is read.

  If readline service value of :redirect-output is truthy (the default
  value) in the supplied rebel line reader service config this
  function will alter the root binding of the *out* var to prevent
  extraneous output from corrupting the read line editors output.

  Once the reading is done it returns the terminal to its original
  settings."
  ;; much of this code is intended to protect the prompt. If the
  ;; prompt gets corrupted by extraneous output it can lead to the
  ;; horrible condition of the readline program thinking the cursor is
  ;; in a different position than it is. We try to prevent this by
  ;; creating a safe writer that will print the output and redraw the
  ;; readline, while ensuring that the printed output has a newline at
  ;; the end.

  ;; We then expand the scope of this print-writer by temorarily
  ;; redefining the root binding of *out* to it.

  ;; The idea being that we want to catch as much concurrant output as
  ;; possible while the readline is enguaged.
  [& [command-executed]]
  (read-line-opts :command-executed (or command-executed "")))

(defn repl-read-line
  "A readline function that converts the Exceptions normally thrown by
  org.jline.reader.impl.LineReaderImpl that signal user interrupt or
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

  (rebel-readline.io.calback-reader/callback-reader #(stream-read-line))"
  []
  (let [request-prompt (Object.)
        request-exit   (Object.)
        possible-result (repl-read-line request-prompt request-exit)]
    (cond
      (= request-prompt possible-result) "\n"
      (= request-exit possible-result) nil
      :else (str possible-result "\n"))))

(defmacro with-readline-in
  "This macro takes a rebel readline service and binds *in* to an a
  `clojure.lang.LineNumberingPushbackReader` that is backed by the
  readline.

  This is perhaps the easiest way to utilize this readline library.

  The downside to using this method is if you are working in a REPL on
  something that reads from the *in* that wouldn't benefit from the
  features of this readline lib. In that case I would look at
  `clj-repl-read` where the readline is only engaged during the read
  portion of the REPL.

  Examples:

  (with-readline-in
    (rebel-readline.clojure.line-reader/create
      (rebel-readline.clojure.service.local/create {:prompt clojure.main/repl-prompt} ))
   (clojure.main/repl :prompt (fn[])))"
  [line-reader & body]
  `(with-line-reader ~line-reader
     (binding [*in* (clojure.lang.LineNumberingPushbackReader.
                     (rebel-readline.io.callback-reader/callback-reader
                      stream-read-line))]
       ~@body)))

(defn basic-line-reader [& opts]
  (api/create-line-reader api/*terminal* nil (apply hash-map opts)))
