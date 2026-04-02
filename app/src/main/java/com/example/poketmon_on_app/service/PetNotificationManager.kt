package com.example.poketmon_on_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.PetState

class PetNotificationManager(
    private val service: Service,
    private val channelId: String,
    private val notificationId: Int
) {

    fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "PokePet",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "PokePet overlay service"
        }
        service.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun build(pokemonName: String, state: PetState): Notification {
        val stateLabel = when (state) {
            PetState.IDLE -> "대기 중"
            PetState.WALK -> "걷는 중"
            PetState.RUN -> "뛰는 중"
            PetState.SLEEP -> "자는 중"
            PetState.REACTION -> "반응 중"
            PetState.DRAGGED -> "드래그 중"
        }

        val sleepLabel = if (state == PetState.SLEEP) "깨우기" else "재우기"

        val stopIntent = PendingIntent.getService(
            service, 0,
            Intent(service, PetOverlayService::class.java).apply {
                action = PetOverlayService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val sleepIntent = PendingIntent.getService(
            service, 1,
            Intent(service, PetOverlayService::class.java).apply {
                action = PetOverlayService.ACTION_TOGGLE_SLEEP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(service, channelId)
            .setContentTitle("$pokemonName - $stateLabel")
            .setContentText("화면에서 놀고 있습니다!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(Notification.Action.Builder(null, sleepLabel, sleepIntent).build())
            .addAction(Notification.Action.Builder(null, "중지", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    fun update(pokemonName: String, state: PetState) {
        val notification = build(pokemonName, state)
        service.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }
}
