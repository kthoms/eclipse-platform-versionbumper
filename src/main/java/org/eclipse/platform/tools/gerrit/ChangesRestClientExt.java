package org.eclipse.platform.tools.gerrit;

import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.restapi.RestApiException;
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
public class ChangesRestClientExt extends ChangesRestClient {

    private GerritRestClient gerritRestClient;
    private ChangeInfosParser changeInfosParser;
    private CommentsParser commentsParser;
    private FileInfoParser fileInfoParser;
    private ReviewerInfosParser reviewerInfosParser;
    private ReviewResultParser reviewResultParser;
    private CommitInfosParser commitInfosParser;
    private AccountsParser accountsParser;
    private MergeableInfoParser mergeableInfoParser;
    private ReviewInfoParser reviewInfoParser;

    public ChangesRestClientExt(GerritRestClient gerritRestClient, ChangeInfosParser changeInfosParser,
            CommentsParser commentsParser, FileInfoParser fileInfoParser, ReviewerInfosParser reviewerInfosParser,
            ReviewResultParser reviewResultParser, CommitInfosParser commitInfosParser, AccountsParser accountsParser,
            MergeableInfoParser mergeableInfoParser, ReviewInfoParser reviewInfoParser) {
        super(gerritRestClient, changeInfosParser, commentsParser, fileInfoParser, reviewerInfosParser,
                reviewResultParser, commitInfosParser, accountsParser, mergeableInfoParser, reviewInfoParser);
        this.gerritRestClient = gerritRestClient;
        this.changeInfosParser = changeInfosParser;
        this.commentsParser = commentsParser;
        this.fileInfoParser = fileInfoParser;
        this.reviewerInfosParser = reviewerInfosParser;
        this.reviewResultParser = reviewResultParser;
        this.commitInfosParser = commitInfosParser;
        this.accountsParser = accountsParser;
        this.mergeableInfoParser = mergeableInfoParser;
        this.reviewInfoParser = reviewInfoParser;
    }

    @Override
    public ChangeApi id(String id) throws RestApiException {
        return new ChangeApiRestClientExt(gerritRestClient, this, changeInfosParser, commentsParser,
            fileInfoParser, reviewResultParser, reviewerInfosParser, commitInfosParser,
            accountsParser, mergeableInfoParser, reviewInfoParser, id);
    }
}
