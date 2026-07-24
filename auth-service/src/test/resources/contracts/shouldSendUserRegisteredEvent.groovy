import org.springframework.cloud.contract.spec.Contract

Contract.make {
    label("user_registered_event")
    input {
        triggeredBy("triggerUserRegisteredEvent()")
    }
    outputMessage {
        sentTo("user-registered-topic")
        body([
            username: "testuser",
            email: "testuser@example.com",
            timestamp: "2026-07-18T00:00:00Z"
        ])
        headers {
            messagingContentType(applicationJson())
        }
    }
}
