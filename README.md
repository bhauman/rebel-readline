# clj-readline

A Clojure library designed to ... well, that part is up to you.

## TODOS

* color prompts

* make sure all color situations are respected

* very strange effect where consecutive similar colors alternate on and off
  - (do (do (do (do ))))

* paredit
  - brackets
  - kill line
  
* el-doc type functionality using post

* indentation
  - indent on tab only when in the leading whitespace of a line
  - indent all following lines (extra feature) (emacs doesn't do this)
  - handle parse failure with decent fallback indentation 
      (at least as far as the line above is indented) 

* tabs break indenting

* have someone test in vi mode

* Look at doing coloring with clj-rewrite???

* look at snippet support

* SELF_INSERT widget is how normal characters make it into buffer
* when pasting remove secondary prompts

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
