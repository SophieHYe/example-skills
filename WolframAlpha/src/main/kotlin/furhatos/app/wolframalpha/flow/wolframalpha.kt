package furhatos.app.wolframalpha.flow

import furhatos.nlu.common.*
import furhatos.flow.kotlin.*
import furhatos.gestures.Gestures

val BASE_URL = "https://api.wolframalpha.com/v1/spoken" // Endpoint for Wolfram Alpha's API with answers tailored for spoken interactions
val APP_ID = "EQKLKA-5EGTXH74UG" // Test account, feel free to use it for testing.
val FAILED_RESPONSES = listOf("No spoken result available", "Wolfram Alpha did not understand your input")
val TIMEOUT = 4000 // 4 seconds

// Start state containing everything except the query to the API
val Start : State = state(Interaction) {

    onEntry {
        furhat.ask("Hi there! Do you have any question?")
    }

    onResponse<Yes>{
        furhat.ask("What is it?")
    }

    onResponse<No>{
        furhat.say("Okay, no worries")
    }

    onResponse {
        // Filler speech and gesture
        furhat.say({
            +"Let's see"
            +Gestures.GazeAway
        }, async = true)

        // Query done in query state below, with its result saved here since we're doing a call
        val response : String? = call(Query(it.text)) as String?

        // Reply to user depending on the returned response
        val reply = when {
            response == null || response == "" -> {
                println("Issues connecting to Wolfram alpha")
                "I'm having issues connecting to my brain. Try again later!"
            }
            FAILED_RESPONSES.contains(response) -> {
                println("No answer to question: ${it.text}")
                "Sorry bro, can't answer that"
            }
            else -> response
        }

        furhat.say(reply)

        furhat.ask("Anything else?")
    }
}

// State to conduct the query to the API
fun Query(question: String) = state {
    onEntry {
        val question = question.replace("+", " plus ").replace(" ", "+")
        val query = "$BASE_URL?i=$question&appid=$APP_ID"

        /* Call to WolframAlpha API made in an anynomous substate (https://docs.furhat.io/flow/#calling-anonymous-states)
         to allow our timeout below to stop the call if it takes to long. Note that you explicitly has to cast the result to a String.
          */
        val response = call {
            khttp.get(query).text
        } as String

        // Return the response
        terminate(response)
    }

    onTime(TIMEOUT) {
        // If timeout is reached, we return nothing
        terminate()
    }
}
