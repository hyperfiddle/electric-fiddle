(ns electric-tutorial.tutorial
  (:require clojure.edn
            #?(:clj [clojure.java.io :as io])
            clojure.string
            contrib.data
            [electric-fiddle.fiddle :refer [Fiddle-fn Fiddle-ns]]
            [electric-fiddle.fiddle-markdown :refer [Custom-markdown]]
            [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-svg3 :as svg]
            [hyperfiddle.router3 :as r]

            [electric-tutorial.two-clocks :refer [TwoClocks]]
            [electric-tutorial.dir-tree :refer [DirTree]]
            [electric-tutorial.toggle :refer [Toggle]]
            [electric-tutorial.system-properties :refer [SystemProperties]]
            [electric-tutorial.backpressure :refer [Backpressure]]
            [electric-tutorial.lifecycle :refer [Lifecycle]]
            [electric-tutorial.forms :refer [Forms]]
            [electric-tutorial.crud :refer [Crud]]
            [electric-tutorial.chat :refer [Chat]]
            [electric-tutorial.input-zoo :refer [InputZoo]]
            [electric-tutorial.webview :refer [Webview]]
            [electric-tutorial.todos :refer [Todos]]
            ;; #_[electric-tutorial.reagent-interop :refer [ReagentInterop]] ; npm install
            [electric-tutorial.svg :refer [SVG]]
            [electric-tutorial.counter :refer [Counter]]
            [electric-tutorial.temperature :refer [TemperatureConverter]]
            [electric-tutorial.timer :refer [Timer]]
            [electric-tutorial.crud-7guis :refer [CRUD]]
            [electric-tutorial.todomvc :refer [TodoMVC]]
            [electric-tutorial.todomvc-composed :refer [TodoMVC-composed]]
            #_[electric-tutorial.explorer :refer [DirectoryExplorer]]

            ))

(def tutorials
  [["Tutorial"
    [`TwoClocks
     `DirTree
     `Webview
     `Lifecycle
     `Backpressure
     `Toggle
     `Counter
     `TemperatureConverter
     `Chat
     `InputZoo
     `Forms
     `Crud
     `Todos
     ; Optimistic Updates
     `TodoMVC
     `TodoMVC-composed
     ; Typeahead
     ; Datagrid with typeahead


     #_`electric-demo.demo-virtual-scroll/VirtualScroll ; virtual scroll Server-streamed virtual pagination over node_modules. Check the DOM!
     #_`electric-demo.wip.demo-stage-ui4/CrudForm
     #_`wip.demo-custom-types/CustomTypes ; Custom transit serializers example
     #_`wip.js-interop/QRCode ; Generate QRCodes with a lazily loaded JS library
     ]]
   ["Demos"
    [#_`DirectoryExplorer
     `SVG
     `SystemProperties
     #_`ReagentInterop
     `Timer
     `CRUD]]
   #_["HFQL"
    [`wip.teeshirt-orders/Webview-HFQL]]])

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
          (r/link [(list (::id prev))]
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
              (when-some [done! ($ e/Token e)]
                (done! ($ r/Navigate! [(list (clojure.edn/read-string (.. e -target -value)))]))))))
        (when next
          (r/link [(list (::id next))]
            (dom/props {:class "user-examples-nav-next"})
            (dom/text (str (title next) " >"))))))))

(def tutorial-path "src/electric_tutorial/")

(def essays
  {`TwoClocks "two_clocks.md"
   `DirTree "dir_tree.md"
   `Webview "webview.md" ; concrete
   `Lifecycle "lifecycle.md"
   `Backpressure "backpressure.md"
   `Toggle "toggle.md"
   `Counter "counter.md"
   `TemperatureConverter "temperature_converter.md"
   `Chat "chat.md"
   `InputZoo "input_zoo.md"
   `Forms "forms.md"
   `Crud "crud.md"
   `Todos "todos.md"
   ; Optimistic updates
   `TodoMVC "todomvc.md"
   `TodoMVC-composed "todomvc_composed.md"
   ; Typeahead
   ; webview dynamoic with typeahead


   ; idioms
   ; file-watcher
   ; nested-documents

   ;; demos
   `Timer "timer.md"
   `CRUD "crud-7guis.md"
   ; webview-scroll
   ; waveform
   ; painter
   ; directory explorer
   `SVG "svg.md"
   ; color picker
   ; fizzbuzz

   ;; unused
   ;`ReagentInterop ""
   ;`SystemProperties "system_properties.md"
   })

(e/defn Fiddles []
  {`TwoClocks TwoClocks
   `DirTree DirTree
   `Webview Webview
   `Lifecycle Lifecycle
   `Toggle Toggle
   `Counter Counter
   `TemperatureConverter TemperatureConverter
   `Backpressure Backpressure
   `Forms Forms
   `Crud Crud
   `Chat Chat
   `InputZoo InputZoo
   `Todos Todos
   `TodoMVC TodoMVC
   `TodoMVC-composed TodoMVC-composed
   `Timer Timer
   `CRUD CRUD
   `SystemProperties SystemProperties
   `SVG SVG
   ;`ReagentInterop ReagentInterop
   })

(e/defn Extensions []
  {'fiddle Fiddle-fn
   'fiddle-ns Fiddle-ns})

(e/defn RedirectLegacyLinks! [link]
  ;; Keep existing links working.
  ;; Demos used to be identified by their fully qualified name - e.g. `hello-fiddle.fiddles/Hello
  ;; They are now represented by an s-expression - e.g. `(Color h s l)
  (if (and (map? link) (ident? (ffirst link)))
    (do ($ r/Navigate! [(list (ffirst link))])
        nil)
    link))

(e/defn Tutorial []
  (e/client
    (dom/style (dom/text (e/server (slurp (io/resource "electric_tutorial/tutorial.css")))))
    (let [[?tutorial] (ffirst ($ RedirectLegacyLinks! r/route))
          ?tutorial   (or ?tutorial `TwoClocks)]
      (dom/h1 (dom/text "Electric Tutorial"))
      (binding [hf/pages ($ Fiddles)]
        ($ Nav ?tutorial false)
        (if-some [essay-filename (get essays ?tutorial)]
          ($ Custom-markdown ($ Extensions) (str tutorial-path essay-filename))
          (dom/h1 (dom/text "Tutorial not found: " ?tutorial)))
        #_($ Nav ?tutorial true)))))

