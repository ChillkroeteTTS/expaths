(ns tourme.components
  (:require [reagent.core :as r]))

(def ReactNative (js/require "react-native"))
(def ReactNativeMaterialIcons (js/require "react-native-vector-icons/MaterialIcons"))

(def Keyboard (.-Keyboard ReactNative))
(def Animated (.-Animated ReactNative))
(def Linking (.-Linking ReactNative))
(def AnimatedValue (.-Value Animated))
(def BackHandler (.-BackHandler ReactNative))
(def Alert (.-Alert ReactNative))

(def animated-view (r/adapt-react-class (.-View Animated)))
(def animated-text (r/adapt-react-class (.-Text Animated)))
(def google-sign-in-button (r/adapt-react-class (.-GoogleSigninButton (js/require "react-native-google-signin"))))
(def google-sign-in-size-wide (.-Wide (.-Size (.-GoogleSigninButton (js/require "react-native-google-signin")))))

(def modal (r/adapt-react-class (.-Modal ReactNative)))
(def image (r/adapt-react-class (.-Image ReactNative)))
(def refresh-control (r/adapt-react-class (.-RefreshControl ReactNative)))
(def toolbar-android (r/adapt-react-class (.-ToolbarAndroid ReactNativeMaterialIcons)))
(def text (r/adapt-react-class (.-Text ReactNative)))
(def view (r/adapt-react-class (.-View ReactNative)))
(def button (r/adapt-react-class (.-Button ReactNative)))
(def checkbox (r/adapt-react-class (.-CheckBox ReactNative)))
(def switch (r/adapt-react-class (.-Switch ReactNative)))
(def picker (r/adapt-react-class (.-Picker ReactNative)))
(def picker-item (r/adapt-react-class (.-Item (.-Picker ReactNative))))
(def section-list (r/adapt-react-class (.-SectionList ReactNative)))
(def scroll-view (r/adapt-react-class (.-ScrollView ReactNative)))
(def touchable-highlight (r/adapt-react-class (.-TouchableHighlight ReactNative)))
(def touchable-opacity (r/adapt-react-class (.-TouchableOpacity ReactNative)))
(def material-icon (r/adapt-react-class (.-default ReactNativeMaterialIcons)))
(def text-input (r/adapt-react-class (.-TextInput ReactNative)))

(defn expect-animation [on-animation-end]
  (let [layoutAnimation  (.-LayoutAnimation ReactNative)
        ease-in-ease-out (.-easeInEaseOut (.-Presets layoutAnimation))]
    (if on-animation-end
      (.configureNext layoutAnimation ease-in-ease-out on-animation-end)
      (.configureNext layoutAnimation ease-in-ease-out))))

(defn connection-issue-alert []
  (js/alert
    "Upps... Something went wrong :(
  Please try it again at a later time."))