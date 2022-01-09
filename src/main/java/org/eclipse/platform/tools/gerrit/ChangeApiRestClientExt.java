package org.eclipse.platform.tools.gerrit;

import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.JsonElement;
import com.urswolfer.gerrit.client.rest.http.GerritRestClient;
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
public class ChangeApiRestClientExt extends ChangeApiRestClient {

    private GerritRestClient gerritRestClient;
    private ChangeInfosParser changeInfosParser;


    public ChangeApiRestClientExt(GerritRestClient gerritRestClient, ChangesRestClient changesRestClient,
            ChangeInfosParser changeInfosParser, CommentsParser commentsParser, FileInfoParser fileInfoParser,
            ReviewResultParser reviewResultParser, ReviewerInfosParser reviewerInfosParser,
            CommitInfosParser commitInfosParser, AccountsParser accountsParser, MergeableInfoParser mergeableInfoParser,
            ReviewInfoParser reviewInfoParser, String id) {
        super(gerritRestClient, changesRestClient, changeInfosParser, commentsParser, fileInfoParser, reviewResultParser,
                reviewerInfosParser, commitInfosParser, accountsParser, mergeableInfoParser, reviewInfoParser, id);
        this.gerritRestClient = gerritRestClient;
        this.changeInfosParser = changeInfosParser;
    }

    
    @Override
    public void rebase(RebaseInput in) throws RestApiException {
        String url = getRequestPath() + "/rebase";
        String json = gerritRestClient.getGson().toJson(in);
        JsonElement jsonElement = gerritRestClient.postRequest(url, json);
        var changeInfo = changeInfosParser.parseSingleChangeInfo(jsonElement);
    }
}
