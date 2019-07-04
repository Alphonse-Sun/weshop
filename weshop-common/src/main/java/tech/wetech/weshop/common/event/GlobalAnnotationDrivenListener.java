package tech.wetech.weshop.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author cjbi@outlook.com
 */
@Component
public class GlobalAnnotationDrivenListener {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @EventListener
    public void handleApplicationStarted(ApplicationStartedEvent ase) {
        log.info("Handling application {} started event.", ase);
        log.info(ase.getApplicationContext().getApplicationName() + "启动成功...");
    }
}
