package com.example.maps

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery


class SetupRabbitMQ {


    private val connectionFactory: ConnectionFactory = ConnectionFactory()

    init {
        connectionFactory.host = ""
        connectionFactory.port = 
        connectionFactory.virtualHost = ""
        connectionFactory.username = ""
        connectionFactory.password = ""
    }
    fun gimmeFactory(): ConnectionFactory{
        return connectionFactory
    }

    fun defaultExchangeAndQueue() : SetupRabbitMQ{
        val newConnection = gimmeFactory().newConnection()
        val channel = newConnection.createChannel()


        channel.queueDeclare("navigation-MM", false, false, true, emptyMap())
        channel.queueBind("navigation-MM", "navigation", "")
        channel.close()
        newConnection.close()
        return this
    }




}