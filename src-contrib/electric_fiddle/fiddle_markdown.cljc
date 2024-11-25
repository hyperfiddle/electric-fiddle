(ns electric-fiddle.fiddle-markdown
  (:refer-clojure :exclude [#?(:cljs Fn)])
  (:require clojure.string
            [contrib.electric-codemirror :refer [CodeMirror]] ; extensions only
            [electric-fiddle.index :refer [Index]] ; why
            #?(:clj [electric-fiddle.read-src :refer [read-ns-src read-src-safe!]])
            [hyperfiddle :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :refer [tests]]
            #?(:clj [markdown.core :refer [md-to-html-string]])))

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

(e/defn Markdown [chunk]
  (e/client (set! (.-innerHTML dom/node) (e/server (some-> chunk md-to-html-string)))))

(e/defn Custom-markdown [extensions essay-filename]
  (e/server
    ; todo not-found
    (let [lines (second (e/diff-by first (e/Offload #(map-indexed vector (parse-sections (slurp essay-filename))))))]
      (e/for [line lines]
        (if (clojure.string/starts-with? line "!")
          (let [[extension & args] (parse-md-directive line)]
            (if-let [F (get extensions extension)]
              (e/apply F args)
              (dom/div (dom/text "Unsupported markdown directive: " (pr-str line)))))
          (dom/div (dom/props {:class "markdown-body user-examples-readme"})
            (Markdown line))))
      #_(dom/h1 (dom/text "Tutorial not found: " essay-filename)))))

; Extensions - optional

(e/defn Target* [target ?wrap]
  (e/client
    (dom/fieldset
      (dom/props {:class ["user-examples-target" (some-> target name)]})
      (dom/legend (dom/text "Result"))
      (let [Target (get pages target ::not-found)
            Wrap (when ?wrap (get pages ?wrap ::not-found))]
        (cond
          (= ::not-found Target) (dom/h1 (dom/text "target not found: " target
                                           " (did you register it in the fiddle index?)"))
          (= ::not-found Wrap) (dom/h1 (dom/text "not found, wrap: " ?wrap))
          (some? Wrap) (Wrap Target)
          () (Target))))))

(e/defn Src* [target & {:keys [ns?]
                        :or {ns? false}}]
  (e/client
    (let [src (e/server (if ns? (read-ns-src target)
                          (read-src-safe! target)))]
      (dom/fieldset
        (dom/props {:class "user-examples-code"})
        (dom/legend (dom/text (namespace target)))
        (CodeMirror {:parent dom/node :readonly true} identity identity src)))))

(e/defn Src+Target [target ?wrap & {:keys [ns?]}]
  #_(dom/pre (dom/text target " " ?wrap " " src))
  #_(dom/pre (dom/text (pr-str props)))
  (dom/div (dom/props {:class "user-examples"})
    (Src* target :ns? ns?)
    (Target* target ?wrap)))

; todo standardize args
(e/defn Fn [& [target-s el-selector ?wrap]] ; todo hide code
  (e/client
    (Src+Target (symbol target-s) (some-> ?wrap symbol)
      :ns? false)))

(e/defn Ns [& [target-s el-selector ?wrap]]
  (e/client
    (Src+Target (symbol target-s) (some-> ?wrap symbol)
      :ns? true)))

(e/defn Fn-src [& [target-s el-selector ?wrap]]
  (e/client
    (binding [dom/node (if-some [s (not-empty el-selector)]
                         (dom/Await-element s) dom/node)]
      (dom/div (dom/props {:class "user-examples"})
        (Src* (symbol target-s) :ns? false)))))

(e/defn Fiddle [& [target-s el-selector ?wrap :as route]] ; direct fiddle link http://localhost:8080/electric-fiddle.fiddle!Fiddle/dustingetz.y-fib!Y-fib
  (if (nil? (seq route)) (Index)
    (Ns "" target-s ?wrap)))

(e/defn Target [& [target-s el-selector ?wrap-s]]
  (e/client
    (binding [dom/node (if-some [s (not-empty el-selector)]
                         (dom/Await-element s) dom/node)]
      (dom/div (dom/props {:class "user-examples"})
        (Target* (symbol target-s) (some-> ?wrap-s symbol))))))

(e/defn Fiddle-markdown-extensions []
  {'fiddle Fn ; compat
   'fiddle-ns Ns ; compat
   'ns Ns
   'fn Fn
   'fn-src Fn-src
   'target Target})