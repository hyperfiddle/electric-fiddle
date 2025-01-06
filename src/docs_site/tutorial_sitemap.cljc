(ns docs-site.tutorial-sitemap
  (:require [hyperfiddle.electric3 :as e]

            ; Part 1
            [electric-tutorial.two-clocks :refer [TwoClocks]]
            [electric-tutorial.system-properties :refer [SystemProperties]]
            [electric-tutorial.dir-tree :refer [DirTree]]
            [electric-tutorial.fizzbuzz :refer [FizzBuzz]]
            [electric-tutorial.webview1 :refer [Webview1]]
            [electric-tutorial.webview2 :refer [Webview2]]
            [electric-tutorial.webview-column-picker :refer [WebviewColumnPicker]]
            [electric-tutorial.webview-diffs :refer [WebviewDiffs]]
            [electric-tutorial.lifecycle :refer [Lifecycle]]
            [electric-tutorial.backpressure :refer [Backpressure]]

            electric-tutorial.inputs-local
            [electric-tutorial.token-explainer :refer [TokenExplainer]]
            [electric-tutorial.form-service :refer [FormsService]]
            [electric-tutorial.forms-inline :refer [Forms-inline]]
            [electric-tutorial.form-list :refer [FormList]]

            ; Part 2
            [electric-tutorial.temperature2 :refer [Temperature2]]
            [electric-tutorial.chat-monitor :refer [ChatMonitor]]
            [electric-tutorial.todos :refer [Todos]]
            [electric-tutorial.todomvc :refer [TodoMVC]]
            [electric-tutorial.todomvc-composed :refer [TodoMVC-composed]]

            ; Misc
            ;; #_[electric-tutorial.reagent-interop :refer [ReagentInterop]] ; npm install
            [electric-tutorial.counter :refer [Counter]]
            [electric-tutorial.svg :refer [SVG]]
            #_[electric-tutorial.timer :refer [Timer]]
            [electric-tutorial.explorer :refer [DirectoryExplorer]]
            #_[electric-tutorial.heroicons-demo :refer [Heroicons]]))

(def tutorial-sitemap
  [["Basics"
    ['two_clocks ; hello world
     'system_properties ; simple query/view topology
     'dir_tree ; complex topology
     'fizzbuzz ; differential
     ]]
   ["Tables"
    ['webview1 ; diffs, IO encapsulation
     'webview2 ; abstraction, lambda demo
     'webview_column_picker
     'webview_diffs
     'explorer
     #_'scroll-seek]]
   ["Language"
    ['lifecycle ; components
     'backpressure
     ]]
   ["Forms"
    ['inputs_local ; self contained
     'amb
     'temperature2
     'token_explainer ; supersedes Toggle, introduce token and service
     'form_explainer ; forms-from-scratch-form
     'forms_inline ; forms3b-inline-submit
     'form_list
     'chat_monitor ; optimistic updates, uses e/amb & e/with-cycle*, adhoc service `Chat ; cookie, pending, security. InputSubmitCreate!
     'todos ; create-new, optimistic updates, service
     'todomvc
     'todomvc_composed
     ]]
   #_["Datagrids"
    [#_`Typeahead
     #_`VirtualScroll
     #_`Datagrid]]
   ["HFQL" [#_`wip.teeshirt-orders/Webview-HFQL]]
   ["Misc"
    ['counter ; on-all, progress, serializable lambda. Oddball demo, todo improve
     #_'temperature
     'svg

     #_`ReagentInterop
     #_`Timer
     #_`wip.demo-custom-types/CustomTypes ; Custom transit serializers example
     #_`wip.js-interop/QRCode ; Generate QRCodes with a lazily loaded JS library
     ]]])

(e/defn TutorialFiddles []
  (merge
    {`TwoClocks TwoClocks
     `SystemProperties SystemProperties
     `DirTree DirTree
     `Lifecycle Lifecycle
     `Webview1 Webview1
     `Webview2 Webview2
     `WebviewColumnPicker WebviewColumnPicker
     `WebviewDiffs WebviewDiffs
     `FizzBuzz FizzBuzz
     `ChatMonitor ChatMonitor
     `Backpressure Backpressure
     }
    (electric-tutorial.inputs-local/Fiddles)
    {; Forms
     `TokenExplainer TokenExplainer
     `FormsService FormsService
     `Forms-inline Forms-inline
     `FormList FormList
     }
    {`Temperature2 Temperature2
     `Todos Todos
     `TodoMVC TodoMVC
     `TodoMVC-composed TodoMVC-composed
     }
    {; Kitchen Sink
     #_#_`Timer Timer
     `Counter Counter
     `SVG SVG
     `DirectoryExplorer DirectoryExplorer
     #_#_`Heroicons Heroicons
     #_#_`ReagentInterop ReagentInterop
     }))

