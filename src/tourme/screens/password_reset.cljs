(ns tourme.screens.password-reset
  (:require [tourme.views.sign-in-up-forms :refer [heading input button container err-text] :as forms]
            [reagent.core :as r]
            [tourme.components :as c]
            [re-frame.core :as rf]
            [tourme.android.navigation :as n]
            [tourme.style :as styl]))

(defn enter-email-screen []
  (let [email   (r/atom "")
        err-msg (r/atom nil)]
    (fn [props]
      [container
       [heading "Reset Password"]
       [c/text {:style (merge styl/font-explanation {:text-align :center})}
        (str "In order to reset your password, please enter your email address below."
             "If we find an associated account we will send you a link to reset your password.")]
       [input {} "" email]
       [err-text err-msg]
       [button {:style {:width :auto}}
        "Reset Password"
        (fn []
          (if (not-empty @email)
            (rf/dispatch [:reset-password @email
                            (fn [] (n/pop props 1) (rf/dispatch [:system-message "Email send"]))
                            (fn [e]
                              (let [inv-email    "Please provide a valid email address"
                                    unknown-user "User with this email address does not exist."
                                    unknown      "Something unexpected happened. We are terribly sorry for the inconvenience. This could be due to a missing network connection."]
                                (c/expect-animation nil)
                                (reset! err-msg
                                        (condp = e
                                          :invalid-email
                                          inv-email
                                          :unknown-user
                                          unknown-user
                                          unknown))))])
            (reset! err-msg "Please enter your email address so we can send you an email with your reset code.")))]])))