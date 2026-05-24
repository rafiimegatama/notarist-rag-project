package com.notarist.ingest.application.port.in;

import com.notarist.ingest.application.command.InitiateIngestionCommand;
import com.notarist.ingest.api.response.UploadUrlResponse;
import com.notarist.core.application.usecase.UseCase;

public interface InitiateIngestionUseCase extends UseCase<InitiateIngestionCommand, UploadUrlResponse> {
    UploadUrlResponse execute(InitiateIngestionCommand command);
}
