(ns electric-tutorial.input-zoo
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer []]))

(declare css)

(e/defn InputZoo []
  (dom/style (dom/text css))
  (dom/p (dom/text "See portals!"))
  (e/for [[Demo selector] (e/amb
                            #_#_#_#_#_
                            [DemoInput* "#zoo_input_star_demo"]
                            [DemoInput "#zoo_input_demo"]
                            [DemoInput! "#zoo_input_bang_demo"]
                            [DemoInputSubmit! "#zoo_input_submit_bang_demo"]
                            [DemoInputSubmitClear! "#zoo_input_submit_clear_bang_demo"])]
    (binding [dom/node (dom/Await-element js/document.body selector)]
      (dom/div ; workaround reverse rendering bug
        (Demo)))))

(def css "
dl.InputZoo { margin: 0; display: grid;  grid-template-columns: 1fr 1fr; row-gap: 1em; }
dl.InputZoo dt { grid-column: 1; font-weight: 700; }
dl.InputZoo dd { grid-column: 2; margin-bottom: .5rem; margin-left: 1em; }
dl.InputZoo p { font-weight: 500; font-size: .9em; }
dl.InputZoo input[type=text] { width: 10ch; }
dl.InputZoo p { margin: 0 0 .5em; }

.user-examples-readme table { max-width:100vw; }
.user-examples-readme table { display: grid; grid-template-columns: 40% 60%; }
.user-examples-readme table tbody {display:contents;}
.user-examples-readme table tr {display:contents;}
.user-examples-readme table .user-examples { font-size: 13px; }
.user-examples-readme table p { margin-top: 1em; }
.user-examples-readme [aria-busy='true'] { background-color: yellow; }
.user-examples-readme input[type=text],
.user-examples-readme input[type=number] { width: 10ch; }
") ; todo check mobile and responsive