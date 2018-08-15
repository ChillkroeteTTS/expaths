(ns tourme.screens.consent
  (:require [tourme.views.sign-in-up-forms :refer [heading input button container err-text] :as forms]
            [reagent.core :as r]
            [tourme.components :as c]
            [re-frame.core :as rf]
            [tourme.android.navigation :as n]
            [tourme.style :as styl]
            [tourme.views.views :as v]))

;;[color clicked-color style on-press caption animation-config]

(defn consent-screen []
  (let [pp      (r/atom false)
        gtac    (r/atom false)
        err-msg (r/atom "")
        logout  (fn [] (rf/dispatch [:sign-out]) true)]
    (.addEventListener c/BackHandler "hardwareBackPress" logout)
    (r/create-class
      {:reagent-render
       (fn [props]
         [container
          [heading "Your Privacy"]
          [c/text {:style (merge styl/font-explanation {:text-align :center :margin-bottom 20})}
           (str "In order for our service to work we need you to accept our Privacy Policy and our Terms of Use."
                "Please read them carefully before going on."
                "You can always revoke your consent by writing an email to expaths@chillkroetenteich.de")]
          [v/toggleable-element pp "Privacy Policy" "https://chillkroetenteich.de/pp.html"]
          [v/toggleable-element gtac "Terms of Use" "https://chillkroetenteich.de/gtac.html"]
          [c/view {:style {:align-items :center}}
           [err-text err-msg]]
          [button {:style {:width :auto}}
           "Explore Expaths"
           (fn []
             (reset! err-msg "")
             (if (and @pp @gtac)
               (rf/dispatch [:accept-policies
                             identity
                             (fn [e] (reset! err-msg "Something went wrong while communicating with the server. Please try again."))])
               (reset! err-msg "Please accept both to continue.")))]])
       :component-will-unmount
       (fn []
         (.removeEventListener c/BackHandler "hardwareBackPress" logout))})))

(defn splash-screen []
  [c/view {:style (merge styl/absolute-filling-object
                         {:background-color styl/sec :justify-content :center :align-items :center})}
   [c/text {:style (merge styl/font-heading1 {:color styl/prim :font-size 40})}
    "E x p a t h s"]])