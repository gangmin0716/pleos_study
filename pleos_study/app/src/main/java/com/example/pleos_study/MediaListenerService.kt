package com.example.pleos_study

import android.service.notification.NotificationListenerService

/**
 * Does nothing by itself. Android only lets an app read other apps' media sessions
 * (MediaSessionManager.getActiveSessions) when it has notification access, and that
 * access is granted to a NotificationListenerService.
 */
class MediaListenerService : NotificationListenerService()
