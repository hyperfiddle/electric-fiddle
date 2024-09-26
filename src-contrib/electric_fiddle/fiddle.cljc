(ns electric-fiddle.fiddle
  (:require clojure.string
            [contrib.electric-codemirror :refer [CodeMirror]]
            [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            #?(:clj [electric-fiddle.read-src :refer [read-ns-src read-src]])
            [electric-fiddle.index :refer [Index]]))

(e/defn Fiddle-impl [target ?wrap src]
  #_(dom/pre (dom/text target " " ?wrap " " src))
  (e/client
    #_(dom/pre (dom/text (pr-str props)))
    (dom/div (dom/props {:class "user-examples"})
      (dom/fieldset
        (dom/props {:class "user-examples-code"})
        (dom/legend (dom/text "Code"))
        ($ CodeMirror {:parent dom/node :readonly true} identity identity src))
      (dom/fieldset
        (dom/props {:class ["user-examples-target" (some-> target name)]})
        (dom/legend (dom/text "Result"))
        (let [Target (get hf/pages target ::not-found)
              Wrap (when ?wrap (get hf/pages ?wrap ::not-found))]
          (cond
            (= ::not-found Target) (dom/h1 (dom/text "not found: " target))
            (= ::not-found Wrap) (dom/h1 (dom/text "not found, wrap: " ?wrap))
            (some? Wrap) ($ Wrap Target)
            () ($ Target)))))))

(e/defn Fiddle-fn [& [alt-text target-s ?wrap :as args]] ; todo hide code
  (let [target (symbol target-s)]
    ($ Fiddle-impl target (some-> ?wrap symbol)
      (e/server (read-src target)))))

(e/defn Fiddle-ns [& [alt-text target-s ?wrap :as args]]
  (let [target (symbol target-s)]
    ($ Fiddle-impl target (some-> ?wrap symbol)
      (e/server (read-ns-src target)))))

(e/defn Fn-src [& [target-s loc-selector :as args]]
  (e/client
    #_(dom/pre (dom/text (pr-str args)))
    (let [target (symbol target-s)
          src (e/server (read-src target))]
      (binding [dom/node (if-some [s (not-empty loc-selector)]
                           (dom/Await-element s) dom/node)]
        (dom/div (dom/props {:class "user-examples"})
          (dom/fieldset
            (dom/props {:class "user-examples-code"})
            (dom/legend (dom/text "Code"))
            (CodeMirror {:parent dom/node :readonly true} identity identity src)))))))

(e/defn Fiddle [& [target-s ?wrap :as route]] ; direct fiddle link http://localhost:8080/electric-fiddle.fiddle!Fiddle/dustingetz.y-fib!Y-fib
  (if (nil? (seq route)) ($ Index)
    ($ Fiddle-ns "" target-s ?wrap)))

(e/defn Fiddle-markdown-extensions []
  {'fiddle Fiddle-fn ; compat
   'fiddle-ns Fiddle-ns ; compat
   'ns Fiddle-ns
   'fn Fiddle-fn
   'fn-src Fn-src})