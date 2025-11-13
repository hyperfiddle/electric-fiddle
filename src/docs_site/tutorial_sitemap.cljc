(ns docs-site.tutorial-sitemap
  (:require
   [electric-tutorial.backpressure :refer [Backpressure]]
   [electric-tutorial.basic-state :refer [BasicState]]
   [electric-tutorial.basictext-input :refer [BasicTextInput]]
   [electric-tutorial.bmi-calc :refer [BMICalculator]]
   [electric-tutorial.button-counter :refer [ButtonCounter]]
   [electric-tutorial.button-counter-callbackless :refer [ButtonCounterCallbackless]]
   [electric-tutorial.button-token :refer [ButtonToken]]
   [electric-tutorial.chat-monitor :refer [ChatMonitor]]
   [electric-tutorial.cons :refer [ConsDemo]]
   [electric-tutorial.counter :refer [Counter]]
   [electric-tutorial.dir-tree :refer [DirTree]]
   [electric-tutorial.dir-tree-explicit :refer [DirTreeExplicit]]
   [electric-tutorial.explorer :refer [DirectoryExplorer]]
   [electric-tutorial.explorer2 :refer [DirectoryExplorer2]]
   [electric-tutorial.fizzbuzz :refer [FizzBuzz]]
   [electric-tutorial.form-list :refer [FormList]]
   [electric-tutorial.form-service :refer [FormsService]]
   [electric-tutorial.forms-inline :refer [Forms-inline]]
   electric-tutorial.inputs-local
   [electric-tutorial.lifecycle :refer [Lifecycle]]
    ;; [electric-tutorial.reagent-interop :refer [ReagentInterop]] ; npm install
   [electric-tutorial.svg :refer [SVG]]
   [electric-tutorial.system-properties :refer [SystemProperties]]
   electric-tutorial.table-window
   electric-tutorial.table-raster
   electric-tutorial.table-tape
   [electric-tutorial.temperature-converter :refer [TemperatureConverter]]
   [electric-tutorial.temperature2 :refer [Temperature2]]
   [electric-tutorial.todomvc :refer [TodoMVC]]
   [electric-tutorial.todomvc-composed :refer [TodoMVC-composed]]
   [electric-tutorial.todos :refer [Todos]]
   [electric-tutorial.token-explainer :refer [TokenExplainer]]
   [electric-tutorial.two-clocks :refer [TwoClocks]]
   [electric-tutorial.typeahead :refer [TypeaheadDemo]]
   [electric-tutorial.webview-column-picker :refer [WebviewColumnPicker]]
   [electric-tutorial.webview-diffs :refer [WebviewDiffs]]
   [electric-tutorial.webview-virtualscroll :refer [OrderForm]]
   [electric-tutorial.webview1 :refer [Webview1]]
   [electric-tutorial.webview2 :refer [Webview2]]
   [hyperfiddle.electric3 :as e]))

(def tutorial-sitemap
  [["Basics"
    ['two_clocks ; hello world
     'system_properties ; simple query/view topology
     'dir_tree ; complex topology
     #_'fizzbuzz ; differential
     ]]
   ["Tables"
    ['webview1 ; diffs, IO encapsulation
     'webview2 ; abstraction, lambda demo
     'webview_column_picker
     #_'webview_diffs
     'explorer
     #_'scroll-seek]]
   ["Language"
    ['lifecycle ; components
     #_'backpressure
     ]]
   ["Forms"
    ['inputs_local ; self contained
     #_'amb
     'temperature2
     'token_explainer ; supersedes Toggle, introduce token and service
     'form_explainer ; forms-from-scratch-form
     'forms_inline ; forms3b-inline-submit
     'form_list
     #_'chat_monitor ; optimistic updates, uses e/amb & e/with-cycle*, adhoc service `Chat ; cookie, pending, security. InputSubmitCreate!
     #_'todos ; create-new, optimistic updates, service
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
     `BasicTextInput BasicTextInput
     `BasicState BasicState
     `ButtonCounter ButtonCounter
     `BMICalculator BMICalculator
     `ButtonToken ButtonToken
     `TemperatureConverter TemperatureConverter
     `SystemProperties SystemProperties
     `DirTreeExplicit DirTreeExplicit
     `DirTree DirTree
     `Lifecycle Lifecycle
     `Webview1 Webview1
     `Webview2 Webview2
     `WebviewColumnPicker WebviewColumnPicker
     `WebviewDiffs WebviewDiffs
     `FizzBuzz FizzBuzz
     `ChatMonitor ChatMonitor
     `Backpressure Backpressure
     `TypeaheadDemo TypeaheadDemo
     }
    {
     `electric-tutorial.table-window/TableWindow electric-tutorial.table-window/TableWindow
     `electric-tutorial.table-raster/TableRaster electric-tutorial.table-raster/TableRaster
     `electric-tutorial.table-tape/TableTape electric-tutorial.table-tape/TableTape
     }

    ;; TODO: Can't specify more than 20 params error if included above.
    {`OrderForm OrderForm
     `ButtonCounterCallbackless ButtonCounterCallbackless
     `ConsDemo ConsDemo}
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
     `DirectoryExplorer2 DirectoryExplorer2
     #_#_`ReagentInterop ReagentInterop
     }))

