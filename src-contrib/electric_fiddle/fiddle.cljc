(ns electric-fiddle.fiddle
  (:refer-clojure :exclude [#?(:cljs Fn)])
  (:require clojure.string
            [contrib.electric-codemirror :refer [CodeMirror]]
            [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            #?(:clj [electric-fiddle.read-src :refer [read-ns-src read-src-safe!]])
            [electric-fiddle.index :refer [Index]]))

(e/defn Target* [target ?wrap]
  (e/client
    (dom/fieldset
      (dom/props {:class ["user-examples-target" (some-> target name)]})
      (dom/legend (dom/text "Result"))
      (let [Target (get hf/pages target ::not-found)
            Wrap (when ?wrap (get hf/pages ?wrap ::not-found))]
        (cond
          (= ::not-found Target) (dom/h1 (dom/text "target not found: " target
                                           " (did you register it in the fiddle index?)"))
          (= ::not-found Wrap) (dom/h1 (dom/text "not found, wrap: " ?wrap))
          (some? Wrap) ($ Wrap Target)
          () ($ Target))))))

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