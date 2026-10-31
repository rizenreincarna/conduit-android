#!/usr/bin/env python3
"""Core sub-project batches: the upstream files, AppState sections and tests each batch ports.

    scripts/core-batches.py           validate, write the plan's batches.json, print the plan's tables
    scripts/core-batches.py --check   validate only; exit 1 on errors

Phase A tasks and skeleton batches (B*) own every Core file; body batches (D*) own the
bodies of Core files over 200 lines; S* own AppState section bodies; T* are integration
tests. Rules: docs/PORTING.md §10–§13. Read back by scripts/portmap.py and scripts/port-check.py.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import portmap as pm  # noqa: E402

TAG, SHA = 'ios/v2.2.1-build-244', '6ddae94189dd21becf2e2892038154c19d47393f'
OUT = pm.BATCHES
SMALL = 200  # files this size or smaller are fully ported in their skeleton batch


def B(id, phase, wave, model, files='', sections='', tests='', new=''):
    return dict(id=id, phase=phase, wave=wave, model=model, files=files.split(),
                sections=sections.split(), tests=[t.replace('~', ' ') for t in tests.split()],
                new=new.split())


PHASE_A_FILES = {
    '2b': 'LogRedaction.swift',
    '4': 'AppLanguage.swift',
    '5': 'Theme.swift CustomAccentColor.swift ScaledFont.swift DynamicTypeSupport.swift ConduitSheetHeader.swift',
    '6': 'ConnectionURLPolicy.swift CloudflareAccess.swift CustomHeaders.swift CustomHeadersEditor.swift',
}

# Waves run in order; within a wave at most 3 batches at a time, started in list order.
BATCHES = [
    B('1', 'A', 1, 'self', new='VersionSchemeTest'),
    B('2a', 'A', 2, 'opus', new='StringsShimTest SwiftCodableTest JSONSerializationShimTest UrlShimTest'),
    B('2b', 'A', 2, 'opus', PHASE_A_FILES['2b'], 'KeychainHelper',
      'LogRedactionTests InMemoryKeychainBackend ScratchDefaults', 'KeychainHelperTest'),
    B('4', 'A', 2, 'sonnet', PHASE_A_FILES['4'], new='L10nFormatTest'),
    B('3', 'A', 3, 'opus', new='URLSessionShimTest'),
    B('5', 'A', 3, 'opus', PHASE_A_FILES['5'], tests='AccentContrastTests', new='SymbolsTest'),
    B('6', 'A', 4, 'sonnet', PHASE_A_FILES['6'],
      tests='ConnectionURLPolicyOriginTests CustomHeadersTests CloudflareAccessTests~(part)'),

    B('B0', 'B', 5, 'sonnet'),
    B('B1a', 'B', 6, 'sonnet', 'Models.swift CapabilityCatalogModels.swift'),
    B('B1b', 'B', 6, 'sonnet'),   # files: the other Core Models files
    B('B2a', 'B', 6, 'sonnet'),   # files: Core Services A–H (not AppState*)
    B('B2b', 'B', 6, 'sonnet'),   # files: Core Services I–Z
    B('B5a', 'B', 7, 'opus'),     # merge wave 6
    B('B3a', 'B', 8, 'opus', sections='AppState Support'),
    B('B3b', 'B', 8, 'sonnet'),   # sections 589–15203 + Core AppState+X files
    B('B3c', 'B', 8, 'sonnet'),   # sections 15204–end except KeychainHelper
    B('B4a', 'B', 8, 'sonnet'),   # files: Views/Components, Views/Login, LoginView
    B('B4b', 'B', 8, 'sonnet'),   # files: the other Core Views, ConduitApp
    B('B5b', 'B', 9, 'opus', new='ConduitTestEnvironment AppStateConstructionTest'),

    B('D0', 'D', 10, 'sonnet',
      'Models.swift PinnedReplies.swift ToolResultPresentation.swift CronScheduleText.swift SessionNesting.swift '
      'PinnedChatOrder.swift UsageReport.swift',
      tests='ChatListSectionsTests ClarifyHistoryRecordTests ClarifyLegacyDecodeTests CronScheduleTextTests '
            'PinnedChatOrderTests ProfileTimeZoneTests ToolResultPresentationTests ToolSearchBridgeTests '
            'ProfileModelDefaultsTests SessionNestingModelTests AppLanguageTests SecurityBoundaryTests'),
    B('D1', 'D', 10, 'sonnet',
      'NativeOAuth.swift NativeAuthClient.swift DashboardBackgroundRequests.swift ServerSignOut.swift '
      'DashboardRequestAuthorizer.swift',
      tests='NativeOAuthTests NativeAuthClientTests NativeAuthClientIntegrationTests DashboardSignInDecisionTests '
            'UserFacingErrorTests DashboardRequestAuthorizerTests ConduitExternalLinkTests ServerSignOutTests '
            'DashboardSocketFixture'),
    B('D2a', 'D', 10, 'sonnet',
      'ConnectionSetupFlow.swift ConnectionSetupTest.swift ConnectionFailure.swift ConnectionSetupDraft.swift '
      'ConnectionSetupApplication.swift',
      tests='ConnectionFailureTests ConnectionSetupFlowTests ConnectionSetupDraftTests ConnectionSetupTestTests '
            'DashboardSignInAgreementTests ConnectionSetupSettingsTests'),
    B('D3', 'D', 10, 'opus', 'HermesClient.swift StreamEventParser.swift',
      tests='StreamEventParserTests AnyCodableTests HermesClientTests+Usage SessionActivityTimestampTests'),

    B('D2b', 'D', 11, 'sonnet',
      'SavedDashboardRegistry.swift ConnectionRepair.swift DashboardFileDownload.swift GatewayRestart.swift '
      'PowerPolicy.swift',
      tests='ConnectionTimelineTests BackgroundConnectionGraceTests PowerPolicyTests GatewayRestartTests '
            'ReconnectBackoffTests PinnedRepliesTests'),
    B('D4', 'D', 11, 'opus',
      'DashboardTicketBridge.swift ChatResumeCoordinator.swift ChatResumeStore.swift ChatResumePolicy.swift '
      'SessionReference.swift HermesSessionReference.swift BotModeModels.swift',
      tests='ChatResumeCoordinatorTests ChatResumePolicyTests ChatResumeStoreTests '
            'MarkdownReferenceLinkTests+HermesSessions DashboardTicketBridgeTests MissingSessionRecoveryTests '
            'ProfileDiscoveryTests HeldDashboardRequests DashboardCookieClearingTests'),
    B('D5a', 'D', 11, 'sonnet',
      'SessionPresentationCache.swift OfflineChatCache.swift SessionContentSearch.swift ChatSync.swift '
      'PersistedTranscriptWindow.swift',
      tests='SessionContentSearchTests SessionCatalogCacheTests ChatSyncTests SessionPresentationCacheTests '
            'SessionRenameTests'),
    B('D5b', 'D', 11, 'sonnet',
      'ChatReadState.swift SessionPinSync.swift ConversationIdentityIndex.swift ChatStepSummaries.swift '
      'ChatTakeover.swift TranscriptPictureMemory.swift ContextUploadPolicy.swift',
      tests='ConversationIdentityIndexTests ConversationIdentityTests ChatTakeoverTests SessionYoloStoreTests '
            'SessionLiveStatusIndexTests PreviewTextTests ContextUploadPolicyTests TranscriptInlineImageTests '
            'ChatTypographyTests+StepSummaries ChatReadStateTests SessionPinSyncTests SessionCatalogLoadTests '
            'TurnStateTests ChatOnScreenTests'),
    B('D6', 'D', 11, 'sonnet',
      'ChatScrollEngine.swift ChatScrollState.swift TranscriptPerformanceInstrumentation.swift '
      'InteractionIdleState.swift Haptics.swift',
      tests='ChatScrollEngineTests ChatScrollStateTests HapticsTests InteractionHapticsTests ResponseHapticStateTests '
            'ChatMessageScrollTargetCacheIncrementalTests TranscriptPerfLedgerContractTests '
            'AppStateVoiceCapabilityTests+InteractionIdle'),
    B('D7', 'D', 11, 'sonnet',
      'ProfileAppearanceStore.swift NotifierPluginUpdate.swift AgentSettings.swift PresentedScreens.swift '
      'Predictive.swift CrashReportOffer.swift SyncedPreferences.swift NotifierPluginStatus.swift AgentPlan.swift '
      'ProfileGlowColors.swift',
      tests='PresentedScreensTests SyncedPreferencesTests ProfileGlowColorTests ConnectionFailureTests+CrashReports '
            'GatewayRestartTests+PluginUpdate GatewayRestartTests+ChatCards ChatTypographyTests+Predictive '
            'HermesVoiceConfigurationServiceTests+AgentSettings'),

    B('S1', 'S', 12, 'sonnet', sections='Support Connection Session Runtime UIState SendingWhileConnecting '
      'AskHermesAboutScreen Capabilities GeminiLiveVoiceMode GPTLiveVoiceMode GPTLiveBilling GrokLiveVoiceMode '
      'LocalLiveVoiceMode WakePhrase Cron LifecycleCoordination OfflineChatCache SendingWhileConnecting2 '
      'SavedChatList Persistence VoiceHistory Outbox Titles Resume FindingChatsFromACall WaitingCall '
      'MinimisedLiveVoice LiveVoiceAttachedToAChat Stars',
      tests='OfflineChatCacheTests AppStateBackgroundSignInTests AppStateTurnSurfaceTests'),
    B('D8', 'D', 12, 'sonnet',
      'AppState+PendingSend.swift AppState+Predictive.swift AppState+NotifierPluginUpdate.swift '
      'AppState+AgentSettings.swift AppState+PresentedScreens.swift AppState+SyncedPreferences.swift '
      'AppState+PinnedReplies.swift'),
    B('D3t', 'D', 12, 'sonnet',
      tests='HermesClientTests MessageNormalizerTests HermesClientTests+DashboardSockets '
            'HermesClientTests+SocketTransport'),
    B('S2', 'S', 12, 'sonnet', sections='ReadState SessionReferences QuotingIntoTheComposer GroupChats '
      'DashboardScopedPresentation RelayGatewaysEachDashboardsHostReported ConnectionManagement ConnectionRepair '
      'SavedDashboards',
      tests='AppStateMultiDashboardTests SavedDashboardRegistryTests ConnectionRepairTests '
            'AppStatePendingLoginFailureTests DashboardProxySignOutTests DashboardSessionIsolationTests'),
    B('S3', 'S', 12, 'opus', sections='AuthoritativeReconciliation ReconnectAndSceneLifecycle '
      'AuthoritativeTurnStateCorrection StreamEventHandling',
      tests='AppStateDecisionFenceTests BufferedEventDeduplicationTests AppStateReasoningStreamTests'),
    B('S4', 'S', 12, 'sonnet', sections='SharedDrafts ConnectionTimeline ForegroundObservationalRefresh '
      'CrossSurfaceTranscriptFreshness SessionManagement OneChatOnTwoDevices NewChat Reactions',
      tests='MessageReactionTests ChatListReturnTests ChatReturnSurfaceTests'),
    B('S5', 'S', 12, 'sonnet', sections='BotMode2 BotManagement ProfilePhotosFromHermes ComposerActions ChatTakeover'),
    B('S6', 'S', 12, 'sonnet', sections='SlashCommands SessionCompression Capabilities2 ToolsCatalogForStepRows '
      'ScheduledJobs SecondaryProfileTitleRecovery ChatSupportSurfaces',
      tests='SlashCommandParityTests AppStateIntegerValueTests AppStateSessionCompressTests RedirectCompatibilityTests'),
    B('S7', 'S', 12, 'sonnet', sections='BotMode ProfilesAndPreferences ClarifyLifecycle AppleWatchVoice '
      'LiveTurnProjection AttachmentHelper AllProfilesInOneChatsList',
      tests='ClarifyBatchStateTests YoloProfileSwitchBookkeepingTests CrossProfilePresentationCacheTests '
            'AppStateMoveSessionTests SessionYoloPersistenceTests'),

    B('D9a', 'D', 13, 'sonnet', 'MarkdownText.swift MarkdownProseRun.swift MarkdownRichContent.swift',
      tests='MarkdownFallbackTests MarkdownRichContentTests MarkdownRichContentTests+LooseOrderedLists '
            'MarkdownRichContentTests+OrderedListStart MarkdownReferenceLinkTests MarkdownTableLayoutTests '
            'ChatTypographyTests+ProseRuns'),
    B('D9b', 'D', 13, 'sonnet',
      'MarkdownLargeDocumentView.swift MarkdownLargeDocument.swift MarkupBlock.swift HTMLPreviewBlock.swift',
      tests='MarkupBlockTests MarkdownLargeDocumentTests MarkdownLargeDocumentTests+HTMLPreview '
            'MarkdownLargeDocumentTests+MediaPreview'),
    B('D9c', 'D', 13, 'sonnet',
      'SelectableTextView.swift MarkdownSelectionCoordinator.swift MarkdownSelectionHandles.swift '
      'SelectableTextCanvas.swift',
      tests='MarkdownSelectionCoordinatorTests ChatTextSelectionTests ChatTypographyTests+DrawnText '
            'DrawnTextLinkTapTests ChatTextSelectionTests+Accent'),
    B('D10a', 'D', 13, 'sonnet', 'ComposerBar.swift', tests='ComposerDraftStoreTests'),
    B('D10b', 'D', 13, 'sonnet',
      'ComposerPasteTextView.swift ComposerAttachments.swift ReturnKeyTextView.swift PastedTextAttachment.swift '
      'ImageGalleryViewer.swift MediaPreviewPresenter.swift',
      tests='ComposerAttachmentPolicyTests ComposerReturnKeyTests ComposerFullEditorTests PastedTextAttachmentTests '
            'ComposerPasteTextViewTests MarkdownLargeDocumentTests+ImageGallery ComposerCameraCaptureTests'),
    B('D11', 'D', 13, 'sonnet',
      'StreamingText.swift ChatTypography.swift ChatCards.swift InlineAudioClip.swift ChatQuote.swift '
      'GatewayMediaTags.swift PredictiveRow.swift ChatCardHeight.swift',
      tests='ChatQuoteTests ChatTypographyTests ChatTypographyTests+FontStyle'),

    B('D12a', 'D', 14, 'sonnet', 'ChatView.swift', tests='ChatTitleScrollTests'),
    B('D12b', 'D', 14, 'sonnet',
      'ChatActivityRows.swift PinnedReplyViews.swift ChatSupportSheets.swift ToolResultViews.swift',
      tests='ChatTypographyTests+StepRows'),
    B('D13a', 'D', 14, 'sonnet',
      'LoginScreens.swift LoginFlowModel.swift LoginView.swift AuthWebPasskeyNotice.swift DashboardCodeScanner.swift '
      'SavedDashboardsView.swift',
      tests='AuthWebViewNavigationPolicyTests TurnstileSubframeBoundaryTests AuthWebPasskeyDetectorTests '
            'LoginFieldNavigationTests LoginFieldNavigationTests+ConnectLink LoginCloudflareHandoffTests '
            'LoginFlowSignInDecisionTests DashboardCookieRestoreTests CloudflareAccessTests~(rest)'),
    B('D13b', 'D', 14, 'sonnet',
      'ConnectionSetupView.swift ConnectionSetupForm.swift ConnectionSetupComponents.swift ConduitPluginViews.swift '
      'RootView.swift'),
    B('D14a', 'D', 14, 'sonnet', 'V2ChatsView.swift V2Shell.swift',
      tests='V2SplitLayoutTests SidebarLayoutTests+V2Filters SidebarLayoutTests+AllProfiles'),
    B('D14b', 'D', 14, 'sonnet',
      'V2TopBar.swift V2ConversationScreen.swift V2SessionRow.swift V2ProjectPage.swift V2FinishSetupCard.swift '
      'ProfilePickerSheet.swift ModelPickerView.swift',
      tests='ModelPickerTests'),
    B('D15', 'D', 14, 'sonnet', 'AuxiliaryViews.swift UsageSettingsViews.swift AgentSettingsViews.swift',
      tests='ProfileModelDefaultsTests+ConfigSchema ConfigFieldLocalizationTests'),
    B('D16', 'D', 14, 'sonnet', 'ConduitApp.swift', tests='AccessibilityLabelAuditTests'),

    B('T1', 'T', 15, 'opus', tests='AppStateChatResumeTests CompactResumeTranscriptTests'),
    B('T2', 'T', 15, 'opus', tests='AppStateForegroundLifecycleTests'),
    B('T3', 'T', 15, 'opus', tests='BotModeTests'),
]

# Upstream tests ported by a later sub-project, whole.
DEFERRED = {
    'bots-tasks-jobs': 'BotManagementTests BotMentionTests CronJobDraftTests GatewayRestartTests+Artifacts '
        'GroupChatModelsTests GroupChatSessionSafetyTests GroupRoomFilesTests '
        'HermesVoiceConfigurationServiceTests+OverlappingSaves KanbanColumnBoardTests KanbanSelectionLayoutTests '
        'KanbanTests KanbanTests+Capabilities KanbanV2CorrectnessTests KanbanV2Tests KanbanV3ATests KanbanV3BTests '
        'KanbanV3CTests KanbanV3DTests ObservedValues PulseFeedTests PulseSettingsTests RichCardTests '
        'WebKitNavigationPolicyConformanceTests WorkspaceFilesTests',
    'voice': 'AVAudioCaptureServiceGenerationTests AppStateContinuousConversationPreferenceTests '
        'AppStateReadAloudTests AppStateServerReplacementSpeechTests AppStateVoiceCapabilityTests '
        'AppStateVoiceCapabilityTests+VoiceModeSwitch ArchivedVoiceCallsTests ChatVoiceButtonTests '
        'DictationClipInboxTests DictationClipStoreTests DictationEngineTests '
        'EchoCancellingVoiceConversationAudioTests GPTLiveVoiceTests GeminiLiveVoiceTests GrokLiveVoiceTests '
        'HapticsVoiceIsolationTests HermesCallWaitingTests HermesCallsTests HermesVoiceConfigurationServiceTests '
        'HermesVoiceConfigurationServiceTests+SetupDefaults HermesVoiceGatewayTimeoutTests '
        'HermesVoiceGatewayTimeoutTests+Keepalive HermesVoiceGatewayTimeoutTests+LiveTranscription '
        'HermesVoiceGatewayTimeoutTests+LocalLive HermesVoiceGatewayTimeoutTests+SpeechServer '
        'LiveVoiceProviderConsentTests LocalLiveJobNoticeTests MessageReadAloudControllerTests OnDeviceSpeechTests '
        'PendingVoiceIntentLifecycleTests QwenLiveVoiceTests ScreenQuestionTests SpeechRecognitionLocaleTests '
        'SpeechRecognitionLocaleTests+WakePhrases VoiceAudioEngineRecoveryTests VoiceAudioSessionConfigurationTests '
        'VoiceAudioSessionCoordinatorTests VoiceAudioSessionQueueTests VoiceBackgroundJobTests '
        'VoiceBackgroundSuspensionTests VoiceBargeInRoutePolicyTests VoiceCallChatTests VoiceChatFinderTests '
        'VoiceConversationControllerTests VoiceConversationControllerTests+LiveTranscription '
        'VoiceHistoryTests VoiceHistoryTests+CaptureCursor VoiceHostToolsTests VoiceJobLinkNoteTests '
        'VoicePushToTalkTests VoiceSetupIssueTests VoiceSpeechDetectorTests VoiceSpokenControlTests '
        'VoiceTestSupport VoiceTestSupportTests WakeConfigurationStoreTests WakeLifecycleTests '
        'WakePhraseCompilerTests WatchAudioBridgeTests WatchCallLogBatchTests WatchVoiceWireTests',
    'notifications': 'GatewayRestartTests+CronNotifications GatewayRestartTests+ReplySources '
        'LiveActivityUpdateThrottleTests NotificationCopyTests NotificationDashboardOwnershipTests '
        'NotificationDashboardOwnershipTests+DeviceToken NotificationDashboardOwnershipTests+PreferenceSync '
        'NotificationE2ETests PushRelayBuildIdentityTests SessionIdentityContractTests WidgetLaunchLinkTests+LiveTurn',
    'phone-data': 'HealthSyncTests LocationSharingTests LocationSpotsTests PhoneSyncTests',
    'widgets-shortcuts-share': 'AppStateScreenQuestionTests AppStateWidgetLaunchTests AskHermesPromptViewTests '
        'ShareDraftTests ShareDraftTests+AppState ShortcutInboxTests ShortcutSendTests WidgetConfigurationTests '
        'WidgetLaunchCoordinatorTests WidgetLaunchLinkTests WidgetSnapshotAppStateTests WidgetSnapshotPublisherTests '
        'WidgetSnapshotTests WidgetVoiceCaptureTests',
    'wear-auto': 'CarPlayTransportRecoveryTests CarPlayVoiceTests',
}

# Upstream tests not ported, with the reason.
EXCLUDED = {
    'InterfaceOrientationTests': 'iOS interface orientations',
    'PrivacyManifestTests': 'iOS privacy manifest',
    'BackgroundTaskHoldTests': 'BackgroundTaskHold is a no-op on Android (spec §4)',
    'ConnectionFailureTests+MetricKit': 'MetricKit is replaced by ApplicationExitInfo (spec §4)',
    'SidebarLayoutTests': 'legacy drawer shell (spec §4); the +V2Filters/+AllProfiles helpers port with D14a',
    'SidebarLayoutTests+CronFilter': 'legacy drawer shell (spec §4)',
    'ChatScrollHostedTests': 'SwiftUI hosting; its behaviors are smoke flow `scroll`',
    'LongContextScalingFixtureTests': 'UITextView binding mechanics; Android uses TextFieldState',
    'SettledMessageIsolationTests': 'SwiftUI body re-evaluation counts',
    'TranscriptPerformanceFixtureTests': 'SwiftUI hosting performance fixture',
    'SelectableTextViewPresentationCacheTests': 'TextKit measurement cache',
    'DormancyHarnessEnvironment': 'support for the excluded hosted tests only',
    'PerformanceFixtureWait': 'support for the excluded hosted tests only',
    'LoopbackHTTPServer': 'replaced by mockwebserver3 (docs/PORTING.md §11)',
}


def lines_of(path):
    spec = pm.MAP['partial'].get(path)
    if spec:
        return sum(hi - lo + 1 for lo, hi in spec['lines'])
    return sum(1 for _ in open(os.path.join(pm.UPSTREAM, path), encoding='utf-8'))


def core_files():
    out = []
    for root in ('Conduit', 'Shared'):
        for d, _, files in os.walk(os.path.join(pm.UPSTREAM, root)):
            for f in files:
                rel = os.path.relpath(os.path.join(d, f), pm.UPSTREAM)
                if f.endswith('.swift') and rel != pm.APPSTATE and pm.classify(rel) in ('core', 'partial'):
                    out.append(rel)
    return sorted(out)


def build():
    errors = []
    core = core_files()
    by_name = {}
    for p in core:
        by_name.setdefault(os.path.basename(p), []).append(p)

    def resolve(name):
        hits = by_name.get(name, [])
        if len(hits) != 1:
            errors.append(f'{name}: {len(hits)} Core files with this name')
            return None
        return hits[0]

    batches = {b['id']: b for b in BATCHES}
    for b in BATCHES:
        b['files'] = [p for p in map(resolve, b['files']) if p]

    # Skeleton batches own every Core file not owned by a Phase A task.
    phase_a = {p for b in BATCHES if b['phase'] == 'A' for p in b['files']}
    rest = [p for p in core if p not in phase_a and p not in batches['B1a']['files']]
    first = lambda p: os.path.basename(p)[0].upper()  # noqa: E731
    rules = [
        ('B1b', lambda p: p.startswith('Conduit/Models/')),
        ('B3b', lambda p: p.startswith('Conduit/Services/AppState+')),
        ('B2a', lambda p: p.startswith('Conduit/Services/') and first(p) <= 'H'),
        ('B2b', lambda p: p.startswith('Conduit/Services/')),
        ('B4a', lambda p: p.startswith(('Conduit/Views/Components/', 'Conduit/Views/Login/'))
         or p == 'Conduit/Views/LoginView.swift'),
        ('B4b', lambda p: True),
    ]
    for p in rest:
        batches[next(i for i, rule in rules if rule(p))]['files'].append(p)

    # AppState sections: B3a owns the class and support code, B3b/B3c split the MARK sections.
    secs = [(lo, os.path.basename(kt)[:-3]) for lo, hi, title, kt in pm.appstate_sections()]
    stems = [s for _, s in secs]
    for b in BATCHES:
        named = []
        for s in b['sections']:
            stem = s if s in stems else 'AppState_' + s
            if stem not in stems:
                errors.append(f'{b["id"]}: unknown AppState section {s}')
            named.append(stem)
        b['sections'] = named
    owned = {'AppState', 'AppState_Support', 'KeychainHelper'}
    batches['B3b']['sections'] = [s for lo, s in secs if s not in owned and lo <= 15203]
    batches['B3c']['sections'] = [s for lo, s in secs if s not in owned and lo > 15203]

    # 1. Phase A and skeleton batches own each Core file once.
    skel = [p for b in BATCHES if b['phase'] in 'AB' for p in b['files']]
    for p in core:
        if skel.count(p) != 1:
            errors.append(f'{p}: owned by {skel.count(p)} Phase A/skeleton batches')
    # 2. Files over SMALL lines get exactly one body batch; smaller ones none.
    body = [p for b in BATCHES if b['phase'] == 'D' for p in b['files']]
    for p in core:
        want = int(lines_of(p) > SMALL and p not in phase_a)
        if body.count(p) != want:
            errors.append(f'{p} ({lines_of(p)} lines): in {body.count(p)} body batches, want {want}')
    # 3. Every section once in the skeleton and, except the class and keychain, once in S1–S7.
    for phases, skip in (('AB', set()), ('S', {'AppState', 'KeychainHelper'})):
        got = [s for b in BATCHES if b['phase'] in phases for s in b['sections']]
        for s in stems:
            if s not in skip and got.count(s) != 1:
                errors.append(f'section {s}: in {got.count(s)} {phases} batches')
    # 4. Every upstream test file is placed once, deferred or excluded.
    upstream = sorted(f[:-6] for f in os.listdir(os.path.join(pm.UPSTREAM, 'ConduitTests')) if f.endswith('.swift'))
    placed = [t for b in BATCHES for t in b['tests']]
    deferred = {sub: names.split() for sub, names in DEFERRED.items()}
    elsewhere = [t for names in deferred.values() for t in names] + list(EXCLUDED)
    for t in set(placed + elsewhere):
        if t.split(' ')[0] not in upstream:
            errors.append(f'test {t}: no ConduitTests/{t.split(" ")[0]}.swift')
    for t in upstream:
        n = placed.count(t) + elsewhere.count(t)
        parts = (placed.count(t + ' (part)'), placed.count(t + ' (rest)'))
        if not ((n == 1 and parts == (0, 0)) or (n == 0 and parts == (1, 1))):
            errors.append(f'test {t}: placed {n}×, part/rest {parts}')
    # 5. Kotlin file names are unique: one package, so XKt classes must not collide.
    mains = [os.path.basename(pm.kotlin_path(p)) for p in core] + [s + '.kt' for s in stems]
    tests = [os.path.basename(pm.kotlin_path(f'ConduitTests/{t}.swift'))
             for t in {t.split(' ')[0] for t in placed}] + [n + '.kt' for b in BATCHES for n in b['new']]
    for n in sorted({n for n in mains + tests if (mains + tests).count(n) > 1}):
        errors.append(f'Kotlin file name {n} is used more than once')
    return batches, deferred, errors


def table(batches):
    """The plan's batch table."""
    rows = ['| Batch | Wave | Model | Ports | Tests |', '|---|---|---|---|---|']
    for b in batches.values():
        size = sum(lines_of(p) for p in b['files'])
        if b['phase'] == 'B' and b['files']:
            ports = f'{len(b["files"])} files, {size} lines'
        else:
            ports = ', '.join(f'{os.path.basename(p)[:-6]} {lines_of(p)}' for p in b['files'])
        if b['sections'] and b['phase'] != 'A':
            ports += (' + ' if ports else '') + f'{len(b["sections"])} sections'
        tests = ', '.join(b['tests'] + [n + ' (new)' for n in b['new']])
        rows.append(f'| {b["id"]} | {b["wave"]} | {b["model"]} | {ports} | {tests} |')
    return '\n'.join(rows)


def main():
    batches, deferred, errors = build()
    for e in errors:
        print('error:', e, file=sys.stderr)
    if errors:
        sys.exit(1)
    if '--check' in sys.argv:
        print('ok')
        return
    out = {'upstream': {'tag': TAG, 'sha': SHA},
           'batches': {i: {k: b[k] for k in ('phase', 'wave', 'model', 'files', 'sections', 'tests', 'new')}
                       for i, b in batches.items()},
           'deferred': deferred, 'excluded': EXCLUDED}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(out, f, indent=1)
        f.write('\n')
    print(table(batches))


if __name__ == '__main__':
    main()
