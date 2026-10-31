#!/usr/bin/env python3
"""Self-check for scripts/portmap.py: python3 scripts/test_portmap.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import portmap as pm  # noqa: E402

M = 'app/src/main/kotlin/app/conduit'
assert pm.kotlin_path('Conduit/Views/V2/V2Shell.swift') == f'{M}/views/v2/V2Shell.kt'
assert pm.kotlin_path('Conduit/Theme.swift') == f'{M}/Theme.kt'
assert pm.kotlin_path('Shared/WatchVoice/CustomAccentColor.swift') == f'{M}/shared/watchvoice/CustomAccentColor.kt'
assert pm.kotlin_path('Conduit/Services/AppState+PendingSend.swift') == f'{M}/services/AppStatePendingSend.kt'
assert pm.kotlin_path('ConduitTests/ChatTypographyTests+StepRows.swift') == \
    'app/src/test/kotlin/app/conduit/ChatTypographyTestsStepRows.kt'
assert pm.kotlin_path('ConduitWatch/WatchApp.swift') is None

assert pm.classify('Conduit/Services/BotModeModels.swift') == 'core'          # port-map core beats Bot*
assert pm.classify('Conduit/Services/BotManagement.swift') == 'partial'
assert pm.classify('Conduit/Services/BotAvatarStore.swift') == 'later:bots-tasks-jobs'
assert pm.classify('Conduit/Views/SidebarView.swift') == 'skip'
assert pm.classify('ConduitUITests/SmokeTests.swift') == 'skip'
assert pm.classify('Shared/WatchVoice/CustomAccentColor.swift') == 'core'      # beats Shared/WatchVoice/*
assert pm.classify('Shared/WatchVoice/WatchVoiceModels.swift') == 'later:voice'
assert pm.classify('Conduit/Models/Models.swift') == 'core'
assert pm.classify('ConduitWatch/X.swift') == 'later:wear-auto'
assert pm.classify('project.yml') == 'outside'
assert pm.classify('Conduit/Models/CapabilityCatalogModels.swift') == 'partial'
assert pm.classify('Conduit/Models/KanbanModels.swift') == 'later:bots-tasks-jobs'
assert pm.classify('Conduit/Models/KanbanStatus.swift') == 'later:bots-tasks-jobs'
assert pm.classify('Conduit/Models/CronJobDraft.swift') == 'later:bots-tasks-jobs'
assert pm.classify('Conduit/Models/CronScheduleText.swift') == 'core'        # usage report dates need it
CAT = 'Conduit/Models/CapabilityCatalogModels.swift'
assert pm._owner_of(CAT, 140, {}).startswith('core')                           # CapabilityToolsetOrdering
assert pm._owner_of(CAT, 520, {}).startswith('core')                           # AgentPluginRow
assert pm._owner_of(CAT, 300, {}).startswith('later:bots-tasks-jobs')
assert pm._owner_of('Conduit/Services/BotManagement.swift', 40, {}).startswith('later:bots-tasks-jobs')

assert pm.pascal_title('// MARK: - Session compression (`session.compress`)') == 'SessionCompression'
assert pm.pascal_title('UI state') == 'UIState'
assert pm.pascal_title('GPT-Live voice mode') == 'GPTLiveVoiceMode'
assert pm.pascal_title("Relay gateways each dashboard's host reported") == 'RelayGatewaysEachDashboardsHostReported'
assert pm.pascal_title('Sending while connecting: what reads private state') == 'SendingWhileConnecting'
files = pm.section_files(['Bot Mode', 'Bot Mode (roster + canonical chat)', 'Keychain Helper'])
assert files == [f'{M}/services/appstate/AppState_BotMode.kt', f'{M}/services/appstate/AppState_BotMode2.kt',
                 f'{M}/services/KeychainHelper.kt'], files

if os.path.exists(os.path.join(pm.UPSTREAM, pm.APPSTATE)):
    secs = pm.appstate_sections()
    assert secs[0][:3] == (1, 586, 'Support') and secs[2][2] == 'Connection', secs[:3]
    assert all(a[1] + 1 == b[0] for a, b in zip(secs, secs[1:])), 'sections must tile the file'
    assert len({s[3] for s in secs}) == len(secs), 'section files must be unique'
    assert any(s[2] == 'Keychain Helper' and s[3].endswith('services/KeychainHelper.kt') for s in secs)
print('ok')
