(ns status-im.contexts.chat.messages.content.view
  (:require
    [legacy.status-im.ui.screens.chat.message.legacy-view :as old-message]
    [quo.core :as quo]
    [quo.foundations.colors :as colors]
    [quo.theme :as quo.theme]
    [react-native.core :as rn]
    [react-native.gesture :as gesture]
    [react-native.platform :as platform]
    [reagent.core :as reagent]
    [status-im.common.not-implemented :as not-implemented]
    [status-im.constants :as constants]
    [status-im.contexts.chat.composer.reply.view :as reply]
    [status-im.contexts.chat.messages.avatar.view :as avatar]
    [status-im.contexts.chat.messages.content.album.view :as album]
    [status-im.contexts.chat.messages.content.audio.view :as audio]
    [status-im.contexts.chat.messages.content.deleted.view :as content.deleted]
    [status-im.contexts.chat.messages.content.image.view :as image]
    [status-im.contexts.chat.messages.content.pin.view :as pin]
    [status-im.contexts.chat.messages.content.reactions.view :as reactions]
    [status-im.contexts.chat.messages.content.status.view :as status]
    [status-im.contexts.chat.messages.content.style :as style]
    [status-im.contexts.chat.messages.content.system.text.view :as system.text]
    [status-im.contexts.chat.messages.content.text.view :as content.text]
    [status-im.contexts.chat.messages.content.unknown.view :as content.unknown]
    [status-im.contexts.chat.messages.drawers.view :as drawers]
    [utils.address :as address]
    [utils.datetime :as datetime]
    [utils.re-frame :as rf]))

(def delivery-state-showing-time-ms 3000)

(defn avatar-container
  [{:keys [content last-in-group? pinned-by quoted-message from]} show-reactions?
   in-reaction-and-action-menu? show-user-info? in-pinned-view?]
  (if (or (and (seq (:response-to content))
               quoted-message)
          last-in-group?
          show-user-info?
          pinned-by
          (not show-reactions?)
          in-reaction-and-action-menu?)
    [avatar/avatar
     {:public-key from
      :size       :small
      :hide-ring? (or in-pinned-view? in-reaction-and-action-menu?)}]
    [rn/view {:padding-top 4 :width 32}]))

(defn author
  [{:keys [response-to
           compressed-key
           last-in-group?
           pinned-by
           quoted-message
           from
           timestamp]}
   show-reactions?
   in-reaction-and-action-menu?
   show-user-info?]
  (when (or (and (seq response-to) quoted-message)
            last-in-group?
            pinned-by
            show-user-info?
            (not show-reactions?)
            in-reaction-and-action-menu?)
    (let [[primary-name secondary-name] (rf/sub [:contacts/contact-two-names-by-identity from])
          {:keys [ens-verified added?]} (rf/sub [:contacts/contact-by-address from])]
      [quo/author
       {:primary-name   primary-name
        :secondary-name secondary-name
        :short-chat-key (address/get-shortened-compressed-key (or compressed-key from))
        :time-str       (datetime/timestamp->time timestamp)
        :contact?       added?
        :verified?      ens-verified}])))

(defn system-message-contact-request
  [{:keys [chat-id timestamp-str from]} type]
  (let [[primary-name _]     (rf/sub [:contacts/contact-two-names-by-identity chat-id])
        contact              (rf/sub [:contacts/contact-by-address chat-id])
        photo-path           (when (seq (:images contact)) (rf/sub [:chats/photo-path chat-id]))
        customization-color  (rf/sub [:profile/customization-color])
        {:keys [public-key]} (rf/sub [:profile/profile])]
    [quo/system-message
     {:type                type
      :timestamp           timestamp-str
      :display-name        primary-name
      :customization-color customization-color
      :photo-path          photo-path
      :incoming?           (not= public-key from)}]))

(defn system-message-content
  [{:keys [content-type quoted-message] :as message-data}]
  (if quoted-message
    [pin/pinned-message message-data]
    (condp = content-type

      constants/content-type-system-text
      [system.text/text-content message-data]

      constants/content-type-system-pinned-message
      [system.text/text-content message-data]

      constants/content-type-community
      [not-implemented/not-implemented
       [old-message/community message-data]]

      constants/content-type-system-message-mutual-event-accepted
      [system-message-contact-request message-data :added]

      constants/content-type-system-message-mutual-event-removed
      [system-message-contact-request message-data :removed]

      constants/content-type-system-message-mutual-event-sent
      [system-message-contact-request message-data :contact-request])))

(declare on-long-press)

(defn- user-message-content-internal
  []
  (let [show-delivery-state? (reagent/atom false)]
    (fn [{:keys [message-data context keyboard-shown? show-reactions? in-reaction-and-action-menu?
                 show-user-info? theme]}]
      (let [{:keys [content-type quoted-message content
                    outgoing outgoing-status pinned-by]} message-data
            first-image                                  (first (:album message-data))
            outgoing-status                              (if (= content-type
                                                                constants/content-type-album)
                                                           (:outgoing-status first-image)
                                                           outgoing-status)
            outgoing                                     (if (= content-type
                                                                constants/content-type-album)
                                                           (:outgoing first-image)
                                                           outgoing)
            context                                      (assoc context
                                                                :on-long-press
                                                                #(on-long-press message-data
                                                                                context
                                                                                keyboard-shown?))
            response-to                                  (:response-to content)
            height                                       (rf/sub [:dimensions/window-height])
            {window-width :width}                        (rn/get-window)
            message-container-data                       {:window-width           window-width
                                                          :padding-right          20
                                                          :padding-left           20
                                                          :avatar-container-width 32
                                                          :message-margin-left    8}]
        [rn/touchable-highlight
         {:accessibility-label (if (and outgoing (= outgoing-status :sending))
                                 :message-sending
                                 :message-sent)
          :underlay-color      (colors/theme-colors colors/neutral-5 colors/neutral-90 theme)
          :style               (style/user-message-content
                                {:first-in-group? (:first-in-group? message-data)
                                 :outgoing        outgoing
                                 :outgoing-status outgoing-status})
          :on-press            (fn []
                                 (if (and platform/ios? keyboard-shown?)
                                   (do
                                     (rf/dispatch [:chat.ui/set-input-focused false])
                                     (rn/dismiss-keyboard!))
                                   (when (and outgoing
                                              (not= outgoing-status :sending)
                                              (not @show-delivery-state?))
                                     (reset! show-delivery-state? true)
                                     (js/setTimeout #(reset! show-delivery-state? false)
                                                    delivery-state-showing-time-ms))))
          :on-long-press       #(on-long-press message-data context keyboard-shown?)}
         [:<>
          (when pinned-by
            [pin/pinned-by-view pinned-by])
          (when (and (seq response-to) quoted-message)
            [reply/quoted-message quoted-message])
          [rn/view
           {:style {:padding-horizontal 4
                    :flex-direction     :row}}
           [avatar-container message-data show-reactions? in-reaction-and-action-menu? show-user-info?
            (:in-pinned-view? context)]
           (into
            (if show-reactions?
              [rn/view]
              [gesture/scroll-view])
            [{:style {:margin-left 8
                      :flex        1
                      :max-height  (when-not show-reactions?
                                     (* 0.4 height))}}
             [author message-data show-reactions? in-reaction-and-action-menu? show-user-info?]
             (condp = content-type
               constants/content-type-text
               [content.text/text-content message-data context]

               constants/content-type-emoji
               [not-implemented/not-implemented [old-message/emoji message-data]]

               constants/content-type-sticker
               [not-implemented/not-implemented [old-message/sticker message-data]]

               constants/content-type-audio
               [audio/audio-message message-data context]

               constants/content-type-image
               [image/image-message 0 message-data context 0 message-container-data]

               constants/content-type-album
               [album/album-message message-data context on-long-press message-container-data]

               constants/content-type-gap
               [rn/view]

               [not-implemented/not-implemented
                [content.unknown/unknown-content message-data]])

             (when @show-delivery-state?
               [status/status outgoing-status])])]
          (when show-reactions?
            [reactions/message-reactions-row message-data
             [rn/view {:pointer-events :none}
              [user-message-content-internal
               {:theme           theme
                :message-data    message-data
                :context         context
                :keyboard-shown? keyboard-shown?
                :show-reactions? false}]]])]]))))

(def user-message-content (quo.theme/with-theme user-message-content-internal))

(defn on-long-press
  [{:keys [deleted? deleted-for-me?] :as message-data} context keyboard-shown?]
  (rf/dispatch [:dismiss-keyboard])
  (rf/dispatch [:show-bottom-sheet
                {:content (drawers/reactions-and-actions message-data context)
                 :border-radius 16
                 :selected-item
                 (if (or deleted? deleted-for-me?)
                   (fn [] [content.deleted/deleted-message message-data])
                   (fn []
                     [rn/view {:pointer-events :none}
                      [user-message-content
                       {:message-data    message-data
                        :context         context
                        :keyboard-shown? keyboard-shown?
                        :show-reactions? true
                        :show-user-info? true}]]))}]))

(defn system-message?
  [content-type]
  (#{constants/content-type-system-text
     constants/content-type-community
     constants/content-type-system-message-mutual-event-accepted
     constants/content-type-system-message-mutual-event-removed
     constants/content-type-system-message-mutual-event-sent
     constants/content-type-system-pinned-message}
   content-type))

(defn message
  [{:keys [pinned-by mentioned content-type last-in-group? deleted? deleted-for-me?]
    :as   message-data} {:keys [in-pinned-view?] :as context} keyboard-shown?]
  [rn/view
   {:style               (style/message-container in-pinned-view? pinned-by mentioned last-in-group?)
    :accessibility-label :chat-item}
   (cond
     (system-message? content-type)
     [system-message-content message-data]

     (or deleted? deleted-for-me?)
     [content.deleted/deleted-message
      (assoc message-data
             :on-long-press
             #(on-long-press message-data
                             context
                             keyboard-shown?))
      context]

     :else
     [user-message-content
      {:message-data    message-data
       :context         context
       :keyboard-shown? keyboard-shown?
       :show-reactions? true}])])
