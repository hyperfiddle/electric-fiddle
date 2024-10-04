(ns electric-tutorial.tutorial
  (:require clojure.edn
            #?(:clj [clojure.java.io :as io])
            clojure.string
            contrib.data
            [electric-fiddle.fiddle :refer [Fiddle-markdown-extensions]]
            [electric-fiddle.fiddle-markdown :refer [Custom-markdown]]
            [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-svg3 :as svg]
            [hyperfiddle.router3 :as r]

            ; Part 1
            [electric-tutorial.two-clocks :refer [TwoClocks]]
            [electric-tutorial.dir-tree :refer [DirTree]]

            [electric-tutorial.lifecycle :refer [Lifecycle]]
            [electric-tutorial.webview1 :refer [Webview1]]
            ;; [electric-tutorial.webview2 :refer [Webview2]] ; can't be deployed to fly, depends on london talk 2024's typeahead
            [electric-tutorial.fizzbuzz :refer [FizzBuzz]]
            [electric-tutorial.toggle :refer [Toggle]]
            [electric-tutorial.counter :refer [Counter]]
            [electric-tutorial.chat :refer [Chat]]
            [electric-tutorial.chat-monitor :refer [ChatMonitor]]
            [electric-tutorial.backpressure :refer [Backpressure]]

            ; Part 2
            [electric-tutorial.temperature :refer [Temperature]]
            [electric-tutorial.temperature2 :refer [Temperature2]]
            [electric-tutorial.forms2-controlled :refer [Forms2-controlled]]
            [electric-tutorial.forms3-crud :refer [Forms3-crud]]
            [electric-tutorial.forms3a-form :refer [Forms3a-form]] ; via Forms3-crud
            [electric-tutorial.forms3b-inline-submit :refer [Forms3b-inline-submit]] ; via Forms3-crud
            #_[electric-tutorial.crud :refer [Crud]]
            [electric-tutorial.todos :refer [Todos]]
            [electric-tutorial.todomvc :refer [TodoMVC]]
            [electric-tutorial.todomvc-composed :refer [TodoMVC-composed]]

            ; Misc
            ;; #_[electric-tutorial.reagent-interop :refer [ReagentInterop]] ; npm install
            [electric-tutorial.svg :refer [SVG]]
            [electric-tutorial.system-properties :refer [SystemProperties]]
            [electric-tutorial.timer :refer [Timer]]
            #_[electric-tutorial.explorer :refer [DirectoryExplorer]]
            ))

(def tutorials
  [["Basics"
    [`TwoClocks ; hello world
     `SystemProperties ; simple query/view topology
     `DirTree ; complex topology
     `Toggle ; token and service, multiplayer
     `Counter ; on-all, progress, serializable lambda
     `FizzBuzz ; differential
     `Lifecycle ; components
     `Webview1 ; diffs, IO encapsulation
     ;; `Webview2 ; abstraction, lambda demo
     `Chat ; cookie, pending, security. InputSubmitCreate!
     `Backpressure]]
   ["Forms"
    [`Temperature ; local form, cycle by side effect
     `Temperature2 ; with-cycle - for ChatMonitor - and e/amb
     `Forms2-controlled ; local form, slightly trickier usage of e/amb
     `Forms3a-form ; transactional form
     `Forms3b-inline-submit ; transactional fields
     `ChatMonitor ; optimistic updates, uses e/amb & e/with-cycle*, adhoc service
     `Todos ; create-new, optimistic updates, service
     `TodoMVC
     `TodoMVC-composed]]
   ["Datagrids"
    [#_`Typeahead
     #_`VirtualScroll
     #_`Datagrid]]
   ["HFQL" [#_`wip.teeshirt-orders/Webview-HFQL]]
   ["Misc"
    [#_`DirectoryExplorer
     `SVG

     #_`ReagentInterop
     `Timer
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

(e/defn Fiddles []
  {`TwoClocks TwoClocks
   `SystemProperties SystemProperties
   `DirTree DirTree
   `Lifecycle Lifecycle
   `Webview1 Webview1
   ;; `Webview2 Webview2
   `FizzBuzz FizzBuzz
   `Toggle Toggle
   `Counter Counter
   `Chat Chat
   `ChatMonitor ChatMonitor
   `Backpressure Backpressure

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
   `Timer Timer

   `SVG SVG
   ;`ReagentInterop ReagentInterop
   })

(e/defn RedirectLegacyLinks! [link]
  ;; Keep existing links working.
  ;; Demos used to be identified by their fully qualified name - e.g. `hello-fiddle.fiddles/Hello
  ;; They are now represented by an s-expression - e.g. `(Color h s l)
  (if (and (map? link) (ident? (ffirst link)))
    (do ($ r/Navigate! [(list (ffirst link))])
        nil)
    link))

(defn namespace-name [qualified-symbol]
  (some-> qualified-symbol namespace
    (clojure.string/split #"\.") last
    (clojure.string/replace "-" "_")))
(comment (namespace-name `Forms3a-form) := "forms3a_form")

(def tutorial-path "src/electric_tutorial/")

(e/defn Consulting-banner []
  (dom/p (dom/text "Hire us! ")
    (dom/a (dom/text "Consutling page here") (dom/props {:href ""}))))

(e/defn Tutorial []
  (e/client
    (dom/style (dom/text (e/server (slurp (io/resource "electric_tutorial/tutorial.css")))))
    (let [[?tutorial] (ffirst ($ RedirectLegacyLinks! r/route))
          ?tutorial   (or ?tutorial `TwoClocks)]
      (Consulting-banner)
      (dom/h1 (dom/text "Tutorial — Electric Clojure v3 ")
        (dom/a (dom/text "(github)") (dom/props {:href "https://github.com/hyperfiddle/electric"})))
      (binding [hf/pages ($ Fiddles)]
        ($ Nav ?tutorial false)
        (let [essay-filename (str tutorial-path (namespace-name ?tutorial) ".md")]
          (Custom-markdown (Fiddle-markdown-extensions) essay-filename))
        #_($ Nav ?tutorial true)))))