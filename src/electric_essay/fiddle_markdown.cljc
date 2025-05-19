(ns electric-essay.fiddle-markdown
  (:refer-clojure :exclude [#?(:cljs Fn)])
  (:require [clojure.string :as str]
            [contrib.electric-codemirror :refer [CodeMirror]] ; extensions only
            [electric-fiddle.fiddle-index :refer [FiddleIndex pages]] ; why
            #?(:clj [electric-essay.read-src :refer [read-ns-src-unreliable read-var-src-safe]])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r] ; for userland
            [hyperfiddle.rcf :refer [tests]]
            #?(:clj [markdown.core :refer [md-to-html-string]])))

#?(:clj (defn parse-sections [md-str]
          (eduction (partition-by #(str/starts-with? % "!"))
            (map #(str/join "\n" %))
            (map #(if (str/starts-with? % "!") [::directive %] [::html (md-to-html-string %)]))
            (str/split-lines md-str))))

(comment (parse-sections (slurp "src/electric_tutorial/two_clocks.md")))

(defn parse-md-directive [s]
  (let [[_ extension alt-text arg arg2] (re-find #"!(.*?)\[(.*?)\]\((.*?)\)(?:\((.*?)\))?" s)]
    [(symbol extension) [alt-text arg arg2]]))

(tests
  (parse-md-directive "!foo[example](https://example.com)")
  := ['foo ["example" "https://example.com" nil]]
  (parse-md-directive "!foo[example](b)(c)")
  := ['foo ["example" "b" "c"]])

(e/defn Custom-markdown [extensions md-content]
  (e/client
    (e/for [[t v] (e/server (e/diff-by {} (e/Offload #(parse-sections (or md-content "")))))]
      (case t
        (::directive) (let [[extension args] (parse-md-directive v)]
                        (if-let [F (get extensions extension)]
                          (e/Apply F args)
                          (dom/div (dom/text "Unsupported markdown directive: " (pr-str v)))))
        (::html) (dom/div (dom/props {:class "markdown-body user-examples-readme"})
                   (set! (.-innerHTML dom/node) v)
                   (e/amb))))))

; Extensions - optional

(e/defn Target-nochrome* [target ?wrap]
  (let [Target (get pages target ::not-found)
        Wrap (when ?wrap (get pages ?wrap ::not-found))]
    (cond
      (= ::not-found Target) (dom/p (dom/text "target not found: " target
                                      " (did you register it in the fiddle index?)"))
      (= ::not-found Wrap) (dom/p (dom/text "wrap not found: " ?wrap))
      (some? Wrap) (r/pop (Wrap Target))
      () (r/pop (Target)))))

(e/defn Target* [target ?wrap]
  (e/client
    (dom/fieldset
      (dom/props {:class ["user-examples-target" (some-> target name)]})
      (dom/legend (dom/text (some-> target name)))
      (Target-nochrome* target ?wrap))))

(e/defn Src* [target & {:keys [ns?]
                        :or {ns? false}}]
  (e/client
    (let [src (e/server (if ns? (read-ns-src-unreliable target)
                          (read-var-src-safe target)))]
      (dom/fieldset
        (dom/props {:class "user-examples-code"})
        (dom/legend (dom/text (if ns? target (namespace target))))
        (CodeMirror {:parent dom/node :readonly true} identity identity src)))))

; todo standardize args
(e/defn Fn [& [target-s el-selector ?wrap]] ; todo hide code
  (e/client
    (let [target-Fn (symbol target-s)
          ?wrap (some-> ?wrap symbol)]
      (dom/div (dom/props {:class "user-examples"})
        (Src* target-Fn :ns? false)
        (Target* target-Fn ?wrap)))))

(e/defn Ns [& [target-s el-selector ?wrap]]
  (e/client
    (let [target-Fn (symbol target-s)
          ?wrap (some-> ?wrap symbol)]
      (dom/div (dom/props {:class "user-examples"})
        (Src* (symbol (namespace target-Fn)) :ns? true)
        (Target* target-Fn ?wrap)))))

(e/defn Fn-src [& [target-s el-selector ?wrap]]
  (e/client
    (when-some [e (if-some [s (not-empty el-selector)]
                    (dom/Await-element js/document.body s) dom/node)]
      (binding [dom/node e]
        (dom/div (dom/props {:class "user-examples"})
          (Src* (symbol target-s) :ns? false))))))

(e/defn Ns-src [& [target-s el-selector ?wrap]]
  (e/client
    (when-some [e (if-some [s (not-empty el-selector)]
                    (dom/Await-element js/document.body s) dom/node)]
      (binding [dom/node e]
        (dom/div (dom/props {:class "user-examples"})
          (Src* (symbol target-s) :ns? true))))))

(e/defn Fiddle [& [target-s el-selector ?wrap :as route]] ; direct fiddle link http://localhost:8080/electric-fiddle.fiddle!Fiddle/dustingetz.y-fib!Y-fib
  (if (nil? (seq route)) (FiddleIndex)
    (Ns "" target-s ?wrap)))

(e/defn Target [& [target-s el-selector ?wrap-s]]
  (e/client
    (when-some [e (if-some [s (not-empty el-selector)]
                    (dom/Await-element js/document.body s) dom/node)]
      (binding [dom/node e]
        (dom/div (dom/props {:class "user-examples"})
          (Target* (symbol target-s) (some-> ?wrap-s symbol)))))))

(e/defn Target-nochrome [& [target-s el-selector ?wrap-s]]
  (e/client
    (when-some [e (if-some [s (not-empty el-selector)]
                    (dom/Await-element js/document.body s) dom/node)]
      (binding [dom/node e]
        (Target-nochrome* (symbol target-s) (some-> ?wrap-s symbol))))))

#_(e/defn Link [& [label target-s _]]
  (r/link ['. [(symbol target-s)]] (dom/text label)))

(e/defn Fiddle-markdown-extensions []
  {'fiddle Fn ; compat
   'fiddle-ns Ns ; compat
   'ns Ns
   'fn Fn
   'fn-src Fn-src
   'ns-src Ns-src
   'target Target
   'target-nochrome Target-nochrome
   ;'link Link -- no inline directives yet
   })
