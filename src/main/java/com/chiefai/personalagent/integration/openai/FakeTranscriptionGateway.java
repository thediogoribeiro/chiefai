package com.chiefai.personalagent.integration.openai;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class FakeTranscriptionGateway implements TranscriptionGateway {
    @Override
    public String transcribe(String fileReference) {
        return "[fake transcription for " + fileReference + "]";
    }
}
