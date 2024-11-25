(ns electric-tutorial.tutorial
  (:require clojure.edn
            #?(:clj [clojure.java.io :as io])
            clojure.string
            contrib.data
            [electric-fiddle.fiddle-index :refer [pages]]
            [electric-fiddle.fiddle-markdown :refer [Custom-markdown Fiddle-markdown-extensions]]
            [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-svg3 :as svg]
            [hyperfiddle.router3 :as r]

            ; Part 1
            [electric-tutorial.two-clocks :refer [TwoClocks]]
            [electric-tutorial.system-properties :refer [SystemProperties]]
            [electric-tutorial.dir-tree :refer [DirTree]]
            [electric-tutorial.fizzbuzz :refer [FizzBuzz]]
            [electric-tutorial.webview1 :refer [Webview1]]
            [electric-tutorial.webview2 :refer [Webview2]]
            [electric-tutorial.lifecycle :refer [Lifecycle]]
            [electric-tutorial.backpressure :refer [Backpressure]]

            [electric-tutorial.counter :refer [Counter]]

            [electric-tutorial.inputs-local :refer
             [InputCicruit DemoInputNaive
              DemoInputCircuit-uncontrolled DemoInputCircuit-controlled DemoInputCircuit-amb DemoInputCircuit-cycle
              DemoInputCircuit4 DemoInputCircuit5 DemoInputCircuit6
              DemoFormSync DemoFormSync-cycle]]
            [electric-tutorial.token-explainer :refer [TokenExplainer]]
            [electric-tutorial.form-explainer :refer [FormExplainer]]
            [electric-tutorial.forms-from-scratch-form :refer [DemoFormServer1]]

            ; Part 2
            [electric-tutorial.temperature :refer [Temperature]]
            [electric-tutorial.temperature2 :refer [Temperature2]]
            [electric-tutorial.forms2-controlled :refer [Forms2-controlled]]
            electric-tutorial.forms3-crud ; inject conn for Forms
            [electric-tutorial.forms3a-form :refer [Forms3a-form]]
            [electric-tutorial.forms3b-inline-submit :refer [Forms3b-inline-submit]]
            [electric-tutorial.chat-monitor :refer [ChatMonitor]]
            [electric-tutorial.todos :refer [Todos]]
            [electric-tutorial.todomvc :refer [TodoMVC]]
            [electric-tutorial.todomvc-composed :refer [TodoMVC-composed]]

            ; Misc
            ;; #_[electric-tutorial.reagent-interop :refer [ReagentInterop]] ; npm install
            [electric-tutorial.svg :refer [SVG]]
            #_[electric-tutorial.timer :refer [Timer]]
            #_[electric-tutorial.explorer :refer [DirectoryExplorer]]))

(def tutorials
  [["Basics"
    ['two_clocks ; hello world
     'system_properties ; simple query/view topology
     'dir_tree ; complex topology
     'fizzbuzz ; differential
     'webview1 ; diffs, IO encapsulation
     'webview2 ; abstraction, lambda demo
     'lifecycle ; components
     'backpressure]]
   ["Forms"
    ['inputs_local ; supersedes TemperatureConverter or embeds
     'token_explainer ; supersedes Toggle, introduce token and service
     'form_explainer ; Transactional forms
     ; Inline forms, keyboard

     'chat_monitor ; optimistic updates, uses e/amb & e/with-cycle*, adhoc service `Chat ; cookie, pending, security. InputSubmitCreate!
     'todos ; create-new, optimistic updates, service
     'todomvc
     'todomvc_composed

;`Temperature ; local form, cycle by side effect
     ;`Forms2-controlled ; local form, no e/amb
     ;`Temperature2 ; with-cycle - for ChatMonitor - and e/amb
     ;`Forms3a-form ; transactional form
     ;`Forms3b-inline-submit ; transactional fields
     ]]
   ["Datagrids"
    [#_`Typeahead
     #_`VirtualScroll
     #_`Datagrid]]
   ["HFQL" [#_`wip.teeshirt-orders/Webview-HFQL]]
   ["Misc"
    ['counter ; on-all, progress, serializable lambda. Oddball demo, todo improve
     #_`DirectoryExplorer
     'svg

     #_`ReagentInterop
     #_`Timer
     #_`wip.demo-custom-types/CustomTypes ; Custom transit serializers example
     #_`wip.js-interop/QRCode ; Generate QRCodes with a lazily loaded JS library
     ]]])

(def tutorials-index (->> tutorials
                       (mapcat (fn [[_group entries]] entries))
                       (map-indexed (fn [idx entry] {::order idx ::id entry}))
                       (contrib.data/index-by ::id)))
(def tutorials-seq (vec (sort-by ::order (vals tutorials-index))))

(defn get-prev-next [page]
  (when-let [order (::order (tutorials-index page))]
    [(get tutorials-seq (dec order))
     (get tutorials-seq (inc order))]))

(defn title [m] (name (::id m)))

(e/defn Nav [page footer?] #_[& [directive alt-text target-s ?wrap :as route]]
  (e/client
    (let [[prev next] (get-prev-next page)]
      #_(println `prev page prev next)
      (dom/div {} (dom/props {:class [(if footer? "user-examples-footer-nav" "user-examples-nav")
                                      (when-not prev "user-examples-nav-start")
                                      (when-not next "user-examples-nav-end")]})
        (when prev
          (r/link ['. [(::id prev)]] ; why nested?
            (dom/props {:class "user-examples-nav-prev"})
            (dom/text (str "< " (title prev)))))
        (dom/div (dom/props {:class "user-examples-select"})
          (svg/svg (dom/props {:viewBox "0 0 20 20"})
            (svg/path (dom/props {:d "M19 4a1 1 0 01-1 1H2a1 1 0 010-2h16a1 1 0 011 1zm0 6a1 1 0 01-1 1H2a1 1 0 110-2h16a1 1 0 011 1zm-1 7a1 1 0 100-2H2a1 1 0 100 2h16z"})))
          (dom/select
            (e/cursor [[group-label entries] (e/diff-by identity tutorials)]
              (dom/optgroup (dom/props {:label group-label})
                (e/cursor [id (e/diff-by identity entries)]
                  (let [m (tutorials-index id)]
                    (dom/option
                      (dom/props {:value (str id) :selected (= page id)})
                      (dom/text (str (inc (::order m)) ". " (title m))))))))
            (when-some [^js e ($ dom/On "change")]
              (let [[done! err] (e/Token e)]
                (when done!
                  (done! ($ r/Navigate! ['. [(clojure.edn/read-string (.. e -target -value))]])))))))
        (when next
          (r/link ['. [(::id next)]] ; why nested?
            (dom/props {:class "user-examples-nav-next"})
            (dom/text (str (title next) " >"))))))))

(e/defn Fiddles []
  {`TwoClocks TwoClocks
   `SystemProperties SystemProperties
   `DirTree DirTree
   `Lifecycle Lifecycle
   `Webview1 Webview1
   `Webview2 Webview2
   `FizzBuzz FizzBuzz
   `Counter Counter
   `ChatMonitor ChatMonitor
   `Backpressure Backpressure

   ; Interlude
   ;`FormsFromScratch FormsFromScratch
   `InputCicruit InputCicruit
   `DemoInputNaive DemoInputNaive
   `DemoInputCircuit-uncontrolled DemoInputCircuit-uncontrolled
   `DemoInputCircuit-controlled DemoInputCircuit-controlled
   `DemoInputCircuit-amb DemoInputCircuit-amb
   `DemoInputCircuit-cycle DemoInputCircuit-cycle
   `DemoInputCircuit4 DemoInputCircuit4
   `DemoInputCircuit5 DemoInputCircuit5
   `DemoInputCircuit6 DemoInputCircuit6
   `DemoFormSync DemoFormSync
   `DemoFormSync-cycle DemoFormSync-cycle

   `TokenExplainer TokenExplainer
   `FormExplainer FormExplainer
   `DemoFormServer1 DemoFormServer1

   ; Part 2
   `Temperature Temperature
   `Temperature2 Temperature2
   `Forms2-controlled Forms2-controlled ; obselete
   `Forms3a-form Forms3a-form
   `Forms3b-inline-submit Forms3b-inline-submit
   `Todos Todos
   `TodoMVC TodoMVC
   `TodoMVC-composed TodoMVC-composed

   ; Kitchen Sink
   ;`Timer Timer
   `SVG SVG
   ;`ReagentInterop ReagentInterop
   })

(defn namespace-name [qualified-symbol]
  (some-> qualified-symbol namespace
    (clojure.string/split #"\.") last
    (clojure.string/replace "-" "_")))
(comment (namespace-name `Forms3a-form) := "forms3a_form")

(def tutorial-path "src/electric_tutorial/")

(e/defn Consulting-banner []
  (dom/p (dom/text "Managers of growth stage businesses, hire us! ")
    (dom/a (dom/text "Consulting brochure here") (dom/props {:href "https://gist.github.com/dustingetz/c40cde24a393a686e26bce73391cd20f"}))))

(e/defn Tutorial []
  (e/client
    (dom/style (dom/text (e/server (slurp (io/resource "electric_tutorial/tutorial.css")))))
    (let [[?essay-filename & _] r/route]
      (if-not ?essay-filename (r/ReplaceState! ['. ['two_clocks]]) ; "two_clocks.md" encodes to /'two_clocks.md'
        (do
          (Consulting-banner)
          (dom/h1 (dom/text "Tutorial — Electric Clojure v3 ")
            (dom/a (dom/text "(github)") (dom/props {:href "https://github.com/hyperfiddle/electric"})))
          (binding [pages (Fiddles)]
            (Nav ?essay-filename false)
            (Custom-markdown (Fiddle-markdown-extensions) (str tutorial-path ?essay-filename ".md"))
            #_(Nav ?tutorial true)))))))