package com.notarist.ingest.application.port.in;

import com.notarist.ingest.api.response.UploadUrlResponse;
import com.notarist.ingest.application.command.InitiateIngestionCommand;

public interface InitiateIngestionUseCase {
    UploadUrlResponse execute(InitiateIngestionCommand command);
}
