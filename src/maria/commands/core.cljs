(ns maria.commands.core
  (:require [clojure.set :as set]
            [magic-tree-codemirror.edit :as edit]
            [goog.events :as events]
            [goog.events.KeyCodes :as key-codes]
            [cljs.pprint :refer [pprint]]
            [re-db.d :as d]
            [clojure.string :as string]
            [re-view-material.icons :as icons])
  (:require-macros [maria.commands.core :refer [defcommand]])
  (:import [goog.events KeyCodes]))

(def commands (atom {}))
(def mappings (atom {}))

(defn modifier->symbol [modifier]
  (let [mac? (string/starts-with? (.. js/navigator -platform) "Mac")
        k (if (and (not mac?) (= modifier :command)) :control modifier)]
    (get {:control        "^"
          :command        "⌘"
          :shift          "⇧"
          :option         "⎇"
          :enter          "↩"
          :backspace      "⌫"
          :forward-delete "⌦"
          :left           "←"
          :right          "→"
          :up             "↑"
          :down           "↓"
          :home           "↖"
          :end            "↘"} k)))

(defn show-key [k]
  (or (modifier->symbol k)
      (name k)))

(def keycode->modifier {(.-META KeyCodes)  :command
                        (.-CTRL KeyCodes)  :command
                        (.-ALT KeyCodes)   :option
                        (.-SHIFT KeyCodes) :shift})

(defn init-listeners []
  (let [clear-keys #(d/transact! [[:db/add :commands :modifiers-down #{}]
                                  [:db/add :commands :which-key/active? false]])
        which-key-delay 500]

    (clear-keys)

    (events/listen js/window "keydown"
                   (fn [e]
                     (d/transact! [[:db/add :commands :last-key (.fromCharCode js/String (.-keyCode e))]])
                     (when-let [modifier (keycode->modifier (.-keyCode e))]
                       (d/transact! [[:db/update-attr :commands :modifiers-down conj modifier]
                                     [:db/update-attr :commands :timeouts conj (js/setTimeout #(let [keys-down (d/get :commands :modifiers-down)]
                                                                                                 (when (and (seq keys-down)
                                                                                                            (not= keys-down #{:shift}))
                                                                                                   (d/transact! [[:db/add :commands :which-key/active? true]]))) which-key-delay)]]))))
    (events/listen js/window "keyup"
                   (fn [e]
                     (when-let [modifier (keycode->modifier (.-keyCode e))]
                       (doseq [timeout (d/get :commands :timeouts)]
                         (js/clearTimeout timeout))
                       (d/transact! [[:db/update-attr :commands :modifiers-down disj modifier]])
                       (when (empty? (d/get :commands :modifiers-down))
                         (d/transact! [[:db/add :commands :which-key/active? false]])))))

    (events/listen js/window #js ["blur" "focus"] #(when (= (.-target %) (.-currentTarget %))
                                                     (clear-keys)))))

(defonce _ (init-listeners))

(defn get-commands [keys-pressed]
  (->> (get-in @mappings [keys-pressed :exec])
       (map @get-commands)))

(defn get-hints [keys-pressed]
  (keep (fn [[keyset results]]
          (when (set/subset? keys-pressed keyset)
            (when (:exec results)                           ;; take off this condition later for multi-step keysets
              {:keyset  keyset
               :results results}))) @mappings))

(defcommand :copy-at-point
            ["Cmd-C"]
            ""
            (:copy-at-point edit/commands))

(defcommand :kill
            ["Ctrl-K"]
            "Cut to end of line / node"
            (:kill edit/commands))

(defcommand :cut-at-point
            ["Cmd-X"]
            "Cuts current highlight"
            (:cut-at-point edit/commands))

(defcommand :delete-at-point
            ["Cmd-Backspace"]
            "Deletes current highlight"
            (:delete-at-point edit/commands))

(defcommand :copy-at-point
            ["Cmd-C"]
            "Copies current highlight"
            (:copy-at-point edit/commands))

(defcommand :hop-left
            ["Alt-Left"]
            "Hop cursor left"
            (:hop-left edit/commands))

(defcommand :hop-right
            ["Alt-Right"]
            ""
            (:hop-right edit/commands))

(defcommand :expand-selection
            ["Cmd-1"]
            ""
            (:expand-selection edit/commands))

(defcommand :shrink-selection
            ["Cmd-2"]
            ""
            (:shrink-selection edit/commands))

(defcommand :comment-line
            ["Cmd-/"]
            ""
            (:comment-line edit/commands))

(defcommand :uneval-at-point
            ["Cmd-."]
            ""
            (:uneval-at-point edit/commands))

(defcommand :slurp
            ["Shift-Cmd-K"]
            ""
            (:slurp edit/commands))


(pprint @mappings)

;; icons for shift, command, option

;; ctrl/command
;; alt/option
;; shift

;; navigator.platform
;; isMac == navigator.platform.indexOf('Mac') > -1
;; https://stackoverflow.com/questions/10527983/best-way-to-detect-mac-os-x-or-windows-computers-with-javascript-or-jquery
;; Mac*  MacIntel
;; iOs - iP* iPad, iPhone, iPod