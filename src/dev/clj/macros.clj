(ns macros)
; See: https://github.com/shaunlebron/How-To-Debug-CLJS
;
; Case 1: Show the state of a bunch of variables.
;
;   > (inspect a b c)
;
;   a => 1
;   b => :foo-bar
;   c => ["hi" "world"]
;
; Case 2: Print an expression and its result.
;
;   > (inspect (+ 1 2 3))   ; Ensure browser console logging is at info level
;
;   (+ 1 2 3) => 6
;

(defn- inspect-1 [expr]
  `(let [result# ~expr]
     (js/console.info (str (pr-str '~expr) " => " (pr-str result#)))
     result#))

; Ensure log level in the browser console is at info level
(defmacro inspect [& exprs]
  `(do ~@(map inspect-1 exprs)))

; -------------------------------------------------------------------
; BREAKPOINT macro
; (use to stop the program at a certain point,
; then resume with the browser's debugger controls)
; NOTE: only works when browser debugger tab is open

(defmacro breakpoint []
  '(do (js* "debugger;")
       nil)) ; (prevent "return debugger;" in compiled javascript)

;; http://brownsofa.org/blog/2014/08/03/debugging-in-clojure-tools
(defmacro defntraced
  "Define a function with its inputs and output logged to the console."
  [sym & body]
  (let [[_ _ [_ & specs]] (macroexpand `(defn ~sym ~@body))
        new-specs
        (map
         (fn [[args body]]
           (let [prns (for [arg args]
                        `(js/console.log (str '~arg) "=" (pr-str ~arg)))]
             (list
              args
              `(do
                 (js/console.groupCollapsed (str '~sym " " '~args))
                 ~@prns
                 (let [res# ~body]
                   (js/console.log "=>" (pr-str res#))
                   (js/console.groupEnd)
                   res#)))))
         specs)]
    `(def ~sym (fn ~@new-specs))))

;; http://brownsofa.org/blog/2014/08/03/debugging-in-clojure-tools
(defmacro dlet [bindings & body]
  `(let [~@(mapcat (fn [[n v]]
                     (if (or (vector? n) (map? n))
                       [n v]
                         ;[n v '_ `(println (name '~n) ":" ~v)]))
                       [n v '_ `(js/console.info (str (name '~n) ":" ~v))]))
                   (partition 2 bindings))]
     ~@body))

;; https://eli.thegreenplace.net/2017/notes-on-debugging-clojure-code/#id3
(defmacro dcond
  [& clauses]
  (when clauses
    (list
     'if
     (first clauses)
     (if (next clauses)
       `(do (println (str "dcond " '~(first clauses)))
            ~(second clauses))
       (throw (IllegalArgumentException.
               "cond requires an even number of forms")))
     (cons 'dcond (nnext clauses)))))
