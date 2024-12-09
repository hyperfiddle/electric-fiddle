(ns ui-demo.scratch
  (:require [hyperfiddle.electric-css3 :as css]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-svg3 :as svg]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.ui.context-menu :as menu]
            [hyperfiddle.ui.spinner]
            [hyperfiddle.ui.disclosure :as dis]
            [hyperfiddle.ui.accordion :as acc]
            [hyperfiddle.ui.stepper :as step]
            [hyperfiddle.ui.typeahead :as t]
            [hyperfiddle.ui.wizard :as wiz]
            ))

(def lorem "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt, enim ac tempor consectetur, justo lorem lobortis magna, eu tincidunt diam sem sed nulla. Nullam gravida arcu sit amet ante congue, eu porttitor est rhoncus. Morbi molestie lorem vel venenatis posuere. Praesent ultricies vehicula ornare. Aenean ornare euismod risus. Donec diam massa, lacinia in commodo in, egestas non nisl. Pellentesque quis mollis est.")

(e/defn Tag [label]
  (dom/div (dom/props {:class "tag"})
    (dom/span (dom/text label))
    (dom/button (dom/text "×"))))

(e/defn Scratch []
  (dom/div
    (dom/link (dom/props {:rel :stylesheet, :href "/user/ui_demo.css"}))
    (dom/props {:class "page"})
    (dom/h1 (dom/text "UI Scratch zone"))
    (dom/hr)

    (acc/accordion
      (acc/entry {::acc/open? true}
        (acc/header (dom/h2 (dom/text "Typeahead")))
        (acc/body (t/Typeahead {:user/name "Alice"}
                    :Options (e/fn [search] (e/diff-by :user/name (filter (comp #(clojure.string/includes? % search) :user/name)
                                                                    [{:user/name "Alice"}
                                                                     {:user/name "Bob"}
                                                                     {:user/name "Charlie"}])))
                    :option-label :user/name)))

      (acc/entry {}
        (acc/header (dom/h2 (dom/text "tag picker")))
        (acc/body (Tag lorem)))

      (acc/entry {}
        (acc/header
          (dom/h2 (dom/text "Spinner")))
        (acc/body
          (hyperfiddle.ui.spinner/spinner (dom/props {:class "w-8 h-8"}))))

      (acc/entry {}
        (acc/header
          (dom/h2 (dom/text "Menu")))
        (acc/body
          (menu/menu {::menu/open? true}
            (menu/items
              (dom/props {:class "z-10 shadow-lg rounded flex flex-col gap-[1px] bg-gray-300 border border-gray-200"})
              (let [item-class "px-4 py-2 cursor-pointer bg-white hover:bg-gray-100 flex gap-2 items-center"]
                (menu/item
                  (dom/props {:class item-class})
                  (dom/text "Entry 1"))
                (menu/item
                  (dom/props {:class item-class})
                  (dom/text "Entry 2"))
                (menu/item
                  (dom/props {:class item-class})
                  (dom/text "Entry 3")))))))

      (acc/entry {}
        (acc/header
          (dom/h2 (dom/text "Context Menu")))
        (acc/body
          (menu/menu {::menu/context-menu? true}
            (menu/items
              (dom/props {:class "z-10 shadow-lg rounded flex flex-col gap-[1px] bg-gray-300 border border-gray-200"})
              (let [item-class "px-4 py-2 cursor-pointer bg-white hover:bg-gray-100 flex gap-2 items-center"]
                (menu/item
                  (dom/props {:class item-class})
                  (dom/text "Entry 1"))
                (menu/item
                  (dom/props {:class item-class})
                  (dom/text "Entry 2"))
                (menu/item
                  (dom/props {:class item-class})
                  (dom/text "Entry 3"))))
            (dom/div
              (dom/text "outer area, click to close menu")
              (dom/props {:class "flex flex-col items-center" :style {:width "400px", :height "250px", :background-color "lightgray"}})
              (dom/On "click" menu/close! nil)
              (dom/div
                (dom/props {:style {:width "200px", :height "200px", :background-color "whitesmoke"}})
                (dom/text "Right click to open menu")
                (dom/On "click" #(.stopPropagation %) nil)
                (dom/On "contextmenu" #(menu/open! nil %) nil)
                )))))

      (acc/entry {}
        (acc/header
          (dom/h2 (dom/text "Disclosure")))
        (acc/body
          (dis/disclosure {}
            (dis/button (dom/text "click me to toggle"))
            (dis/panel {} (dom/text "disclosed content")))))

      (acc/entry {}
        (acc/header
          (dom/h2 (dom/text "Accordion")))
        (acc/body
          (acc/accordion
            (css/style (css/rule ".accordion .disclosure-button svg" {:width "1rem"})) ; FIXME bug in heroicons.electric3
            (acc/entry {::acc/open? true}
              (acc/header (dom/h2 (dom/text "One")))
              (acc/body (dom/text "content one")))
            (acc/entry {}
              (acc/header (dom/h2 (dom/text "Two")))
              (acc/body (dom/text "content two")))
            (acc/entry {}
              (acc/header (dom/h2 (dom/text "Three")))
              (acc/body (dom/text "content three")))
            )))
      (acc/entry {}
        (acc/header
          (dom/h2 (dom/text "Stepper")))
        (acc/body
          (step/stepper
            (dom/props {:class "space-y-2"})
            (step/step {::step/completed? true} (dom/text "Create account"))
            (step/step {::step/current? true} (dom/text "Profile information"))
            (step/step {} (dom/text "Theme"))
            (step/step {} (dom/text "Preview")))))
      #_(acc/entry {} ; WIP
        (acc/header
          (dom/h2 (dom/text "Wizard")))
        (acc/body
          (wiz/Wizard {::wiz/steps [["Create account" (e/fn []
                                                        (dom/h2 (dom/text "Create account"))
                                                        (do
                                                          (dom/button (dom/text "< ") (dom/On "click" #(wiz/prev-step!) nil))
                                                          (dom/button (dom/text " >" ) (dom/On "click" #(wiz/next-step!) nil))))]
                                    ["Profile information"
                                     (e/fn []
                                       (dom/h2 (dom/text "Profile information"))
                                       (do
                                         (dom/button (dom/text "< ") (dom/On "click" #(wiz/prev-step!) nil))
                                         (dom/button (dom/text " >" ) (dom/On "click" #(wiz/next-step!) nil)))
                                       )]
                                    ["Theme" (e/fn []
                                               (dom/h2 (dom/text "Theme"))
                                               (do
                                                 (dom/button (dom/text "< ") (dom/On "click" #(wiz/prev-step!) nil))
                                                 (dom/button (dom/text " >" ) (dom/On "click" #(wiz/next-step!) nil)))
                                               )]
                                    ["Preview"
                                     (e/fn []
                                       (dom/h2 (dom/text "Preview"))
                                       (do
                                         (dom/button (dom/text "< ") (dom/On "click" #(wiz/prev-step!) nil))
                                         (dom/button (dom/text " >" ) (dom/On "click" #(wiz/next-step!) nil)))
                                       )]]}
            (e/fn [Step]
              (dom/div
                (dom/props {:class "flex"})
                (wiz/WizardStepper)
                (dom/div (dom/props {:class "px-2 border border-slate-200 shadow-lg rounded"})
                         (Step))))))))))