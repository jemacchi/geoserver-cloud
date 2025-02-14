/*
 * (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.catalog.plugin;

import java.rmi.server.UID;
import java.util.LinkedHashSet;
import java.util.List;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFacade;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CatalogRepository;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.catalog.impl.LayerGroupStyle;
import org.geoserver.catalog.impl.LayerInfoImpl;
import org.geoserver.catalog.impl.ModificationProxy;
import org.geoserver.catalog.impl.ResolvingProxy;
import org.geoserver.catalog.impl.ResourceInfoImpl;
import org.geoserver.catalog.impl.StoreInfoImpl;
import org.geoserver.catalog.impl.StyleInfoImpl;
import org.geoserver.catalog.plugin.CatalogInfoLookup.LayerGroupInfoLookup;
import org.geoserver.catalog.plugin.CatalogInfoLookup.LayerInfoLookup;
import org.geoserver.catalog.plugin.CatalogInfoLookup.MapInfoLookup;
import org.geoserver.catalog.plugin.CatalogInfoLookup.NamespaceInfoLookup;
import org.geoserver.catalog.plugin.CatalogInfoLookup.ResourceInfoLookup;
import org.geoserver.catalog.plugin.CatalogInfoLookup.StoreInfoLookup;
import org.geoserver.catalog.plugin.CatalogInfoLookup.StyleInfoLookup;
import org.geoserver.catalog.plugin.CatalogInfoLookup.WorkspaceInfoLookup;
import org.geoserver.ows.util.OwsUtils;

/**
 * Default catalog facade implementation using in-memory {@link CatalogRepository repositories} to
 * store the {@link CatalogInfo}
 */
@Slf4j
public class DefaultMemoryCatalogFacade extends RepositoryCatalogFacadeImpl implements CatalogFacade {

    public DefaultMemoryCatalogFacade() {
        this(null);
    }

    public DefaultMemoryCatalogFacade(Catalog catalog) {
        super(catalog);
        setNamespaceRepository(new NamespaceInfoLookup());
        setWorkspaceRepository(new WorkspaceInfoLookup());
        setStoreRepository(new StoreInfoLookup());
        setLayerRepository(new LayerInfoLookup());
        setResourceRepository(new ResourceInfoLookup((LayerInfoLookup) getLayerRepository()));
        setLayerGroupRepository(new LayerGroupInfoLookup());
        setMapRepository(new MapInfoLookup());
        setStyleRepository(new StyleInfoLookup());
    }

    @Override
    public void resolve() {
        getWorkspaceRepository().findAll().forEach(this::resolve);
        getNamespaceRepository().findAll().forEach(this::resolve);
        getStoreRepository().findAll().forEach(this::resolve);
        getStyleRepository().findAll().forEach(this::resolve);
        getResourceRepository().findAll().forEach(this::resolve);
        getLayerRepository().findAll().forEach(this::resolve);
        getLayerGroupRepository().findAll().forEach(this::resolve);
        getMapRepository().findAll().forEach(this::resolve);
    }

    protected void resolve(LayerInfo layer) {
        setId(layer);

        ResourceInfo resource = ResolvingProxy.resolve(getCatalog(), layer.getResource());
        if (resource != null) {
            resource = unwrap(resource);
            layer.setResource(resource);
        }

        StyleInfo style = ResolvingProxy.resolve(getCatalog(), layer.getDefaultStyle());
        if (style != null) {
            style = unwrap(style);
            layer.setDefaultStyle(style);
        }

        LinkedHashSet<StyleInfo> styles = new LinkedHashSet<>();
        for (StyleInfo s : layer.getStyles()) {
            s = ResolvingProxy.resolve(getCatalog(), s);
            s = unwrap(s);
            styles.add(s);
        }
        ((LayerInfoImpl) layer).setStyles(styles);
    }

    protected void resolve(LayerGroupInfo layerGroup) {
        setId(layerGroup);

        LayerGroupInfoImpl lg = (LayerGroupInfoImpl) layerGroup;

        resolveLayerGroupLayers(lg.getLayers());
        resolveLayerGroupStyles(lg.getLayers(), lg.getStyles());
        // now resolves layers and styles defined in layer group styles
        for (LayerGroupStyle groupStyle : lg.getLayerGroupStyles()) {
            resolveLayerGroupLayers(groupStyle.getLayers());
            resolveLayerGroupStyles(groupStyle.getLayers(), groupStyle.getStyles());
        }
    }

    private void resolveLayerGroupStyles(List<PublishedInfo> assignedLayers, List<StyleInfo> styles) {
        for (int i = 0; i < styles.size(); i++) {
            StyleInfo s = styles.get(i);
            if (s != null) {
                PublishedInfo assignedLayer = assignedLayers.get(i);
                StyleInfo resolved = null;
                if (assignedLayer instanceof LayerGroupInfo) {
                    // special case we might have a StyleInfo representing
                    // only the name of a LayerGroupStyle thus not present in Catalog.
                    // We take the ref and create a new object
                    // without searching in catalog.
                    String ref = ResolvingProxy.getRef(s);
                    if (ref != null) {
                        StyleInfo styleInfo = new StyleInfoImpl(getCatalog());
                        styleInfo.setName(ref);
                        resolved = styleInfo;
                    }
                }
                if (resolved == null) resolved = unwrap(ResolvingProxy.resolve(getCatalog(), s));

                styles.set(i, resolved);
            }
        }
    }

    private void resolveLayerGroupLayers(List<PublishedInfo> layers) {
        for (int i = 0; i < layers.size(); i++) {
            PublishedInfo published = layers.get(i);

            if (published != null) {
                PublishedInfo resolved = resolveLayerGroupLayers(published);
                layers.set(i, resolved);
            }
        }
    }

    private PublishedInfo resolveLayerGroupLayers(@NonNull PublishedInfo published) {
        PublishedInfo resolved = unwrap(ResolvingProxy.resolve(getCatalog(), published));
        // special case to handle catalog loading, when nested publishables might not be loaded.
        if (resolved == null && (published instanceof LayerInfo || published instanceof LayerGroupInfo)) {
            resolved = published;
        }
        return resolved;
    }

    protected void resolve(StyleInfo style) {
        setId(style);

        // resolve the workspace
        WorkspaceInfo ws = style.getWorkspace();
        if (ws != null) {
            WorkspaceInfo resolved = ResolvingProxy.resolve(getCatalog(), ws);
            if (resolved != null) {
                resolved = unwrap(resolved);
                style.setWorkspace(resolved);
            } else {
                log.info(
                        """
                        Failed to resolve workspace for style {}.
                        This means the workspace has not yet been added to the catalog, keep the proxy around
                        """,
                        style.getName());
            }
        }
    }

    protected void resolve(MapInfo map) {
        setId(map);
    }

    protected void resolve(WorkspaceInfo workspace) {
        setId(workspace);
    }

    protected void resolve(NamespaceInfo namespace) {
        setId(namespace);
    }

    protected void resolve(StoreInfo store) {
        setId(store);
        StoreInfoImpl s = (StoreInfoImpl) store;

        // resolve the workspace
        WorkspaceInfo resolved = ResolvingProxy.resolve(getCatalog(), s.getWorkspace());
        if (resolved != null) {
            resolved = unwrap(resolved);
            s.setWorkspace(resolved);
        } else {
            log.info(
                    """
                    Failed to resolve workspace for store {}.
                    This means the workspace has not yet been added to the catalog, keep the proxy around
                    """,
                    store.getName());
        }
    }

    protected void resolve(ResourceInfo resource) {
        setId(resource);
        ResourceInfoImpl r = (ResourceInfoImpl) resource;

        // resolve the store
        StoreInfo store = ResolvingProxy.resolve(getCatalog(), r.getStore());
        if (store != null) {
            store = unwrap(store);
            r.setStore(store);
        }

        // resolve the namespace
        NamespaceInfo namespace = ResolvingProxy.resolve(getCatalog(), r.getNamespace());
        if (namespace != null) {
            namespace = unwrap(namespace);
            r.setNamespace(namespace);
        }
    }

    protected void setId(Object o) {
        if (OwsUtils.get(o, "id") == null) {
            String uid = new UID().toString();
            String id = "%s-%s".formatted(o.getClass().getSimpleName(), uid);
            OwsUtils.set(o, "id", id);
        }
    }

    @Nullable
    public static <T> T unwrap(@Nullable T obj) {
        return ModificationProxy.unwrap(obj);
    }
}
