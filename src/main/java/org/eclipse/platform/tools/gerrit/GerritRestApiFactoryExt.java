package org.eclipse.platform.tools.gerrit;

import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApi;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import com.urswolfer.gerrit.client.rest.http.HttpRequestExecutor;
import com.urswolfer.gerrit.client.rest.http.changes.ChangeApiRestClient;

/**
 * Extension to add missing rebase feature in {@link ChangeApiRestClient}.
 */
public class GerritRestApiFactoryExt extends GerritRestApiFactory {
    @Override
    public GerritRestApi create(GerritAuthData authData, HttpRequestExecutor httpRequestExecutor,
            HttpClientBuilderExtension... httpClientBuilderExtensions) {
        return new GerritApiImplExt(authData, httpRequestExecutor, httpClientBuilderExtensions);
    }
}
