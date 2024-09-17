(ns electric-fiddle.fiddle-markdown
  (:require clojure.string
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :refer [tests]]
            #?(:clj [markdown.core :refer [md-to-html-string]])
            electric-fiddle.index))

#?(:clj (defn parse-sections [md-str]
          (->> md-str clojure.string/split-lines
            (partition-by #(not= \! (first %))) ; isolate the directive lines
            (map #(apply str (interpose "\n" %))))))

(comment (parse-sections (slurp "src/electric_tutorial/two_clocks.md")))

(defn parse-md-directive [s]
  (let [[_ extension alt-text arg arg2] (re-find #"!(.*?)\[(.*?)\]\((.*?)\)(?:\((.*?)\))?" s)]
    [(symbol extension) alt-text arg arg2]))

(tests
  (parse-md-directive "!foo[example](https://example.com)")
  := ['foo "example" "https://example.com" nil]
  (parse-md-directive "!foo[example](b)(c)")
  := ['foo "example" "b" "c"])

(e/defn Custom-markdown [extensions essay-filename]
  (e/server
    (e/for [[_ s] (e/diff-by first (map-indexed vector (parse-sections (slurp essay-filename))))]
      (if (clojure.string/starts-with? s "!")
        (let [[extension & args] (parse-md-directive s)]
          (if-let [F (get extensions extension)]
            (e/apply F args)
            (dom/div (dom/text "Unsupported markdown directive: " (pr-str s)))))
        (dom/div (dom/props {:class "markdown-body user-examples-readme"})
          (e/client (set! (.-innerHTML dom/node) (e/server (some-> s md-to-html-string)))))))))
