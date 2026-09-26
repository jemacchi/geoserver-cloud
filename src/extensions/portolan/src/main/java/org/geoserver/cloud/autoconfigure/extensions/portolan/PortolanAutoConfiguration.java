/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.cloud.autoconfigure.extensions.portolan;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.geoserver.cloud.config.factory.ImportFilteredResource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Auto-configuration for the Portolan WebUI extension.
 *
 * <p>The extension imports the classic GeoServer community module Spring context when the WebUI application starts.
 */
@AutoConfiguration
@ConditionalOnClass(
        name = {
            "org.geoserver.config.GeoServer",
            "org.geoserver.web.GeoServerApplication",
            "org.geoserver.portolan.web.PortolanPage"
        })
@ConditionalOnProperty(name = "geoserver.service.webui.enabled", havingValue = "true", matchIfMissing = false)
@ImportFilteredResource("jar:gs-portolan-.*!/applicationContext.xml")
@Slf4j(topic = "org.geoserver.cloud.autoconfigure.extensions.portolan")
public class PortolanAutoConfiguration {

    @PostConstruct
    void log() {
        log.info("Portolan WebUI extension installed");
    }
}
