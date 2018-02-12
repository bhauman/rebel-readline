# Purpose

Provide a better readline experience for Clojure languages.

It is valid to ask why would Clojure programmers need a better
readline experience, as our tools of choice normally allow us to edit
in the environment of our choice and send off expressions to be
evaluated in a REPL.

While this is true, it doesn't hurt to make the REPL experience more
helpful and pleasurable when we are forced to interact with it
directly at the terminal. These rare situations do pop up, and when
they do the anomalous situation is sometimes associated with some kind
of urgency. In cases where we are trying to debug a live system, and
our tools aren't available, there is no need to make the situation
harder with a spartan terminal UX.

So when you do have to use the REPL, it would be nice for it to
work better.

However, the main reason for this library is for the newcomers to
Clojure. The path for newcomers to create an effective Clojure
programming environment, is varied, difficult and confusing. It
requires a level of investment and discernment that is too high for
the language explorer who has never used a LISP before. As a result
when newcomers come to Clojure the most intelligent decision they can
make, is to not try and negotiate the tooling needed for an editor
REPL connection but rather just use the `clojure.main/repl` or `lein
repl` and/or a edit a file with a familiar editor and constantly
re-run or re-load a script. Thus, they experience a stunted workflow
that is all too familiar in other languages and it is easy to
miss-construe this as the Clojure development experience.

A fluid interactive programming workflow is a fundamental difference
that Clojure offers, yet many newcomers will often never see or
experience it.

When I refer to a fluid interactive programming workflow in Clojure I
am thinking mainly of inline-eval.  Most programmers do not have an
experience of what inline-eval is. They have nothing to compare it
to. They have not used LISPs and SEXPs. You can describe inline eval
and demonstrate it to them until you're blue in the face and they
won't get it.

It is not until a programmer actually experiences inline-eval as a
programming tool that the light goes on. SEXPs start to make more
sense, and the why of LISP starts to dawn.

The idea here is to provide the opportunity to experience inline-eval
at the first REPL a newcomer tries. The idea is to provide a tool that
is sharp enough for newcomers to elegantly solve
[4Clojure](http://www.4clojure.com/) problems and participate in
[Advent Of Code](http://adventofcode.com/) without having to make a
steep investment in an unfamiliar toolchain.

As a bonus, when we provide this experience at the very first REPL,
newcomers will have a base of experience from which they can now draw
from to choose their tooling.

They will understand the availability of online docs, source code, and
apropos. They will understand the capabilities of inline eval and
structural editing. IMHO this experience needs to be communicated as
urgently as the other Clojure features.

# Design priorities

* keep dependency tree very simple and shallow. In order for a library
  like this to be adopted widely across the Clojure tooling system it
  needs to not bring extraneous dependencies.  This means the core
  library should have as few dependencies as possible.  When
  dependencies are required the transient dependencies again should be
  few to none.
  
  JLine is not a small or simple dependency but it is non-negotiable
  at the moment b/c manipulating a terminal and its capabilities in a
  cross platform compatible way is a very difficult problem.

* provide an exceptional readline experience for Clojure programmers
  This experience should transcend currently available readline
  offerings in other languages. Clojure has many advantages (sexps,
  etc) that provide a readline library a great deal of leverage to
  do text manipulation.
  
* as a readline library it shouldn't interfere with the input stream
  when it is not reading a line
  
* as a readline library it is not responsible for REPL output, this
  doesn't mean it can't provide useful utilities that would help the
  library consumer, for example to query the last line read, or to
  redisplay the last line (possibly in place) with a code pointer
  indicating an error.

* open and customizable, following in the example of Emacs this
  library's behavior should be customizable. Behavior should be
  modifiable/programable from the readline itself.
  
  This will allow the people to opt in to additional functionality like 
  paredit while keeping the core as simple as possible.

* timeline priority is to get something useful into the hands of
  programmers sooner than later

* an eventual goal is to abstract the api enough so that it will work
  in on a JS platform, this is not an immediate goal.
