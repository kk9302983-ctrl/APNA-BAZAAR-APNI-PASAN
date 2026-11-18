package com.example.domain

import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus

sealed interface PaymentInitiationResult {
    data class Ready(
        val paymentMethod: PaymentMethod,
        val initialPaymentStatus: PaymentStatus,
        val gatewayRef: String?,
        val notice: String
    ) : PaymentInitiationResult

    data class Failed(val reason: String) : PaymentInitiationResult
}

/**
 * Modular payment gateway abstraction so Razorpay SDK + server webhook verification
 * can be plugged in without changing checkout/order state machines.
 * Client code NEVER marks an online payment as Paid without trusted backend confirmation.
 */
interface PaymentGateway {
    val method: PaymentMethod
    suspend fun initiate(orderNumber: String, amountInr: Double): PaymentInitiationResult
}

class CodPaymentGateway : PaymentGateway {
    override val method: PaymentMethod = PaymentMethod.COD

    override suspend fun initiate(orderNumber: String, amountInr: Double): PaymentInitiationResult {
        return PaymentInitiationResult.Ready(
            paymentMethod = PaymentMethod.COD,
            initialPaymentStatus = PaymentStatus.PENDING,
            gatewayRef = null,
            notice = "Cash on Delivery selected. Pay ${DeliveryCalculator.formatRupees(amountInr)} at your doorstep."
        )
    }
}

class ModularRazorpayGateway(
    private val isGatewayConfigured: Boolean = false
) : PaymentGateway {
    override val method: PaymentMethod = PaymentMethod.RAZORPAY

    override suspend fun initiate(orderNumber: String, amountInr: Double): PaymentInitiationResult {
        val intentRef = "rzp_pending_$orderNumber"
        val notice = if (isGatewayConfigured) {
            "Razorpay checkout initiated. Payment will be confirmed once verified by backend webhook."
        } else {
            "Online Payment created in Awaiting Verification state. Trusted backend Razorpay signature verification is required before marking Paid."
        }
        return PaymentInitiationResult.Ready(
            paymentMethod = PaymentMethod.RAZORPAY,
            initialPaymentStatus = PaymentStatus.AWAITING_VERIFICATION,
            gatewayRef = intentRef,
            notice = notice
        )
    }
}
