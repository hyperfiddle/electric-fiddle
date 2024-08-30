(ns electric-fiddle.fiddle-markdown
  (:require clojure.string
            [electric-fiddle.fiddle :refer [Fiddle-fn]]
            [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            #?(:clj [markdown.core])
            [hyperfiddle.rcf :refer [tests]]
            electric-fiddle.index))

#?(:clj (defn parse-sections [md-str]
          (->> md-str clojure.string/split-lines
            (partition-by #(not= \! (first %))) ; isolate the directive lines
            (map #(apply str (interpose "\n" %))))))

(comment (parse-sections (slurp (essays 'electric-y-combinator))))

(defn md-to-html-string [md-str]
  #?(:clj (markdown.core/md-to-html-string md-str)))

(e/defn Markdown [?md-str]
  (let [html (e/server (some-> ?md-str md-to-html-string))]
    (set! (.-innerHTML dom/node) html)))

(def essays
  {'electric-y-combinator "src/dustingetz/electric_y_combinator.md"
   'hfql-intro "src/dustingetz/hfql_intro.md"
   'hfql-teeshirt-orders "src/dustingetz/hfql_teeshirt_orders.md"

   'demo_two_clocks "src/electric_tutorial/demo_two_clocks.md"})

(defn parse-md-directive [s]
  (let [[_ extension alt-text arg arg2] (re-find #"!(.*?)\[(.*?)\]\((.*?)\)(?:\((.*?)\))?" s)]
    [(symbol extension) alt-text arg arg2]))

(tests
  (parse-md-directive "!foo[example](https://example.com)")
  := ['foo "example" "https://example.com" nil]
  (parse-md-directive "!foo[example](b)(c)")
  := ['foo "example" "b" "c"])

(e/defn ExtensionNotFound [s & directive]
  (e/client (dom/div (dom/text "Unsupported markdown directive: " (pr-str directive)))))

(comment 
  (parse-sections (slurp "src/electric_tutorial/demo_two_clocks.md")))

(e/defn Custom-markdown [extensions essay-filename]
  (e/server
    (e/cursor [s (e/diff-by identity (parse-sections (slurp essay-filename)))]
      (if (clojure.string/starts-with? s "!")
        (let [[extension & args] (parse-md-directive s)]
          (if-let [F (get extensions extension)]
            (e/apply F args)
            ($ ExtensionNotFound s)))
        (e/client
          (dom/div
            (dom/props {:class "markdown-body user-examples-readme"})
            ($ Markdown s)))))))
