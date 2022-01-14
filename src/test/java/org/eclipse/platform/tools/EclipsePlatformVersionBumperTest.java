package org.eclipse.platform.tools;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.eclipse.jgit.transport.PushResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EclipsePlatformVersionBumperTest {

  @InjectMocks
  @Spy
  EclipsePlatformVersionBumper instance;

  @Test
  void test() {
    fail("Not yet implemented");
  }

  @Test
  void getChangeIdFromPushResult() {
    // given
    String message = "\n" + "Processing changes: new: 1 (\\)\n" + "Processing changes: new: 1 (|)\n"
        + "Processing changes: new: 1 (/)\n" + "Processing changes: refs: 1, new: 1 (/)\n"
        + "Processing changes: refs: 1, new: 1 (/)\n" + "Processing changes: refs: 1, new: 1, done    \n"
        + "commit 0224f78: ----------\n" + "commit 0224f78: Reviewing commit: 0224f782\n"
        + "commit 0224f78: Authored by: Karsten Thoms <karsten.thoms@karakun.com>\n" + "commit 0224f78: \n"
        + "commit 0224f78: Reviewing commit: 0224f782b3a60aea331615d28838ce8c17967a0d\n"
        + "commit 0224f78: Authored by: Karsten Thoms <karsten.thoms@karakun.com>\n"
        + "commit 0224f78: Eclipse user 'kthoms'(author) is a committer on the project.\n"
        + "commit 0224f78: Eclipse user 'kthoms'(committer) is a committer on the project.\n" + "commit 0224f78: \n"
        + "commit 0224f78: This commit passes Eclipse validation.\n" + "\n" + "SUCCESS\n" + "\n"
        + "  https://git.eclipse.org/r/c/platform/eclipse.platform.ui/+/189621 4.23 version bump [NEW]\n"
        + "\n";

    var pushResult = mock(PushResult.class);
    doReturn(message).when(pushResult).getMessages();

    // when
    var changeId = instance.getChangeId(pushResult);

    // then
    Assertions.assertEquals(Optional.of("189621"), changeId);
  }

}
