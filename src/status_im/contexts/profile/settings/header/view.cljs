(ns status-im.contexts.profile.settings.header.view
  (:require [clojure.string :as string]
            [quo.core :as quo]
            [quo.theme :as quo.theme]
            [react-native.core :as rn]
            [status-im.contexts.profile.settings.header.avatar :as header.avatar]
            [status-im.contexts.profile.settings.header.header-shape :as header.shape]
            [status-im.contexts.profile.settings.header.style :as style]
            [status-im.contexts.profile.settings.header.utils :as header.utils]
            [status-im.contexts.profile.settings.visibility-sheet.view :as visibility-sheet]
            [status-im.contexts.profile.utils :as profile.utils]
            [utils.re-frame :as rf]))

(defn- f-view
  [{:keys [theme scroll-y]}]
  (let [{:keys [public-key emoji-hash] :as profile} (rf/sub [:profile/profile-with-image])
        online?                                     (rf/sub [:visibility-status-updates/online?
                                                             public-key])
        status                                      (rf/sub
                                                     [:visibility-status-updates/visibility-status-update
                                                      public-key])
        customization-color                         (rf/sub [:profile/customization-color])
        full-name                                   (profile.utils/displayed-name profile)
        profile-picture                             (profile.utils/photo profile)
        emoji-string                                (string/join emoji-hash)
        {:keys [status-title status-icon]}          (header.utils/visibility-status-type-data status)]
    [:<>
     [header.shape/view
      {:scroll-y            scroll-y
       :customization-color customization-color
       :theme               theme}]
     [rn/view {:style style/avatar-row-wrapper}
      [header.avatar/view
       {:scroll-y            scroll-y
        :display-name        full-name
        :online?             online?
        :customization-color customization-color
        :profile-picture     profile-picture}]
      [rn/view {:style {:margin-bottom 4}}
       [quo/dropdown
        {:background     :blur
         :size           :size-32
         :type           :outline
         :icon?          true
         :no-icon-color? true
         :icon-name      status-icon
         :on-press       #(rf/dispatch [:show-bottom-sheet
                                        {:shell?  true
                                         :theme   :dark
                                         :content (fn [] [visibility-sheet/view])}])}
        status-title]]]
     [quo/text-combinations
      {:title-accessibility-label :username
       :container-style           style/title-container
       :emoji-hash                emoji-string
       :title                     full-name}]]))

(def view (quo.theme/with-theme f-view))
