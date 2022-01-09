package org.eclipse.platform.tools.gerrit;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.gerrit.extensions.api.changes.Changes;
import com.urswolfer.gerrit.client.rest.GerritApiImpl;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;
import com.urswolfer.gerrit.client.rest.http.HttpRequestExecutor;
import com.urswolfer.gerrit.client.rest.http.accounts.AccountsParser;
import com.urswolfer.gerrit.client.rest.http.changes.ChangeApiRestClient;
import com.urswolfer.gerrit.client.rest.http.changes.ChangesRestClient;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.ChangeInfosParser;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.CommentsParser;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.CommitInfosParser;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.FileInfoParser;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.MergeableInfoParser;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.ReviewInfoParser;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.ReviewResultParser;
import com.urswolfer.gerrit.client.rest.http.changes.parsers.ReviewerInfosParser;

/**
 * Extension to add missing rebase feature in {@link ChangeApiRestClient}.
 */
public class GerritApiImplExt extends GerritApiImpl {
    private final GerritRestClient gerritRestClient;

    private final Supplier<ChangesRestClient> changesRestClient = Suppliers.memoize(new com.google.common.base.Supplier<ChangesRestClient>() {
        @Override
        public ChangesRestClient get() {
            return new ChangesRestClientExt(
                    gerritRestClient,
                    new ChangeInfosParser(gerritRestClient.getGson()),
                    new CommentsParser(gerritRestClient.getGson()),
                    new FileInfoParser(gerritRestClient.getGson()),
                    new ReviewerInfosParser(gerritRestClient.getGson()),
                    new ReviewResultParser(gerritRestClient.getGson()),
                    new CommitInfosParser(gerritRestClient.getGson()),
                    new AccountsParser(gerritRestClient.getGson()),
                    new MergeableInfoParser(gerritRestClient.getGson()),
                    new ReviewInfoParser(gerritRestClient.getGson()));
        }
    });

    public GerritApiImplExt(GerritAuthData authData, HttpRequestExecutor httpRequestExecutor,
            HttpClientBuilderExtension[] httpClientBuilderExtensions) {
        super(authData, httpRequestExecutor, httpClientBuilderExtensions);
        this.gerritRestClient = new GerritRestClient(authData, httpRequestExecutor, httpClientBuilderExtensions);
    }

    @Override
    public Changes changes() {
        return changesRestClient.get();
    }
}
