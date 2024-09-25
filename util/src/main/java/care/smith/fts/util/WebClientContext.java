package care.smith.fts.util;

import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;

public record WebClientContext(WebClientSsl ssl) {

}
