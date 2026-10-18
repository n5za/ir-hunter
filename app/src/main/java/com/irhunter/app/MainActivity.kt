package com.irhunter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.irhunter.core.model.*
import com.irhunter.core.scan.Candidates

class MainActivity : ComponentActivity() {
    private val model: HunterViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF63D9BE),
                background = Color(0xFF10171C), surface = Color(0xFF19232B))) {
                Surface(Modifier.fillMaxSize()) { HunterApp(model) }
            }
        }
    }
    override fun onStop() { model.stop(); super.onStop() }
}

@Composable
private fun HunterApp(vm: HunterViewModel) {
    var tab by rememberSaveable { mutableStateOf("Library") }
    var categoryName by rememberSaveable { mutableStateOf(DeviceCategory.TV.name) }
    val category = DeviceCategory.valueOf(categoryName)
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<RemoteProfile?>(null) }
    var deleting by remember { mutableStateOf<RemoteProfile?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readImport) }
    val opened = vm.profiles.firstOrNull { it.id == openedId }
    val candidates = Candidates.ordered(vm.profiles, category, brand, model)
    val offProfiles = vm.profiles.filter { it.saved && it.id in selected }
    BackHandler(openedId != null || vm.busy) { vm.stop(); openedId = null }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Text("IR HUNTER", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text("Offline infrared remotes", style = MaterialTheme.typography.bodyMedium)
        Text(vm.transmitter.hardwareMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        if (vm.busy) Button(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) { Text("STOP") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Library", "Saved", "All OFF").forEach { label ->
                TextButton(onClick = { vm.stop(); tab = label; openedId = null }, enabled = !vm.busy) {
                    Text(if (tab == label) "• $label" else label)
                }
            }
        }
        if (vm.message.isNotBlank()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text(vm.message, modifier = Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.notice("") }) { Text("Dismiss") }
                }
            }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (opened != null) {
            LazyColumn(modifier = Modifier.testTag("contentList"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TextButton(onClick = { vm.stop(); openedId = null }) { Text("Back") }
                    Text(opened.name, style = MaterialTheme.typography.headlineSmall)
                    Text("${opened.category.displayName} · ${opened.brand} · ${opened.model}")
                    Text("Source: ${opened.source}\nLicense: ${opened.license}", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { vm.update(opened.copy(saved = !opened.saved)) }) { Text(if (opened.saved) "Unsave" else "Save remote") }
                        TextButton(onClick = { edit = opened }, enabled = !vm.busy) { Text("Edit") }
                        TextButton(onClick = { deleting = opened }, enabled = !vm.busy) { Text("Delete") }
                    }
                    Text("Only commands present in this profile are shown. Sent does not mean the device responded.")
                }
                items(opened.commands, key = { it.name }) { c ->
                    Column {
                        Button(onClick = { vm.send(c) }, enabled = vm.transmitter.available && !vm.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(c.name.replace('_', ' ')) }
                        if (c.isOff) {
                            Row {
                                Checkbox(checked = c.confirmedOff, onCheckedChange = { checked ->
                                    vm.update(opened.copy(commands = opened.commands.map { if (it.name == c.name) it.copy(confirmedOff = checked) else it }))
                                }, enabled = !vm.busy)
                                Text("I physically verified this OFF command", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else if (tab == "All OFF") {
            LazyColumn(modifier = Modifier.testTag("contentList"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("All Devices OFF", style = MaterialTheme.typography.headlineSmall)
                    Text("Select saved devices. Verified OFF is preferred. Power Toggle may turn an already-off device ON.")
                }
                if (vm.profiles.none { it.saved }) item { Text("No saved remotes. Open an imported profile and choose Save remote.") }
                items(vm.profiles.filter { it.saved }, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp)) {
                            Checkbox(checked = p.id in selected, enabled = !vm.busy && p.offCommand() != null,
                                onCheckedChange = { selected = if (it) selected + p.id else selected - p.id })
                            Column(Modifier.weight(1f)) {
                                Text(p.name)
                                val command = p.offCommand()
                                Text(command?.let { "${it.name} · " + if (it.confirmedOff) "OFF verified by you" else if (it.isToggle) "Power Toggle: may turn ON" else "OFF not yet verified" } ?: "No usable OFF command")
                                vm.offStatuses[p.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                item {
                    Button(onClick = { confirmOff = true }, enabled = offProfiles.isNotEmpty() && vm.transmitter.available && !vm.busy) { Text("Review and send (${offProfiles.size})") }
                    Text("STOP cancels remaining commands. Android cannot interrupt a single IR pattern already in flight (under 2 seconds).", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            val visible = vm.profiles.filter { (tab != "Saved" || it.saved) && it.category == category &&
                (brand.isBlank() || it.brand.contains(brand, true)) && (model.isBlank() || it.model.contains(model, true) || it.name.contains(model, true)) }
            LazyColumn(modifier = Modifier.testTag("contentList"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { CategoryPicker(category, enabled = !vm.busy) { categoryName = it.name } }
                item {
                    OutlinedTextField(brand, { brand = it }, label = { Text("Search brand") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { brand = "" }, enabled = !vm.busy) { Text("Unknown Brand — include all") }
                    OutlinedTextField(model, { model = it }, label = { Text("Search model / profile") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { model = "" }, enabled = !vm.busy) { Text("Unknown Model — include all") }
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.busy) { Text("Import .ir remote") }
                }
                if (tab == "Library") item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Auto Scan", style = MaterialTheme.typography.titleMedium)
                            Text("${candidates.size} matching power candidates. One every 3 seconds. Point at one device and stop as soon as it responds.")
                            if (!category.supportsGuidedScan) Text("This category uses stateful commands. Open an imported remote and test its exact commands manually.")
                            Button(onClick = { vm.scan(candidates) }, enabled = candidates.isNotEmpty() && category.supportsGuidedScan && !vm.busy && vm.transmitter.available) { Text("Start scan") }
                            if (vm.scanProgress.isNotBlank()) Text(vm.scanProgress)
                            vm.lastCandidate?.let { c ->
                                Text("Last sent: ${c.profile.name} / ${c.command.name}")
                                Button(onClick = vm::confirmCandidate) { Text("Device responded — stop and save") }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) item {
                    Text("No matching profiles. Try another brand or category, or import a Flipper .ir file to add your own remote. Bundled codes only cover common devices — your model may need a manual import.")
                }
                items(visible, key = { it.id }) { p ->
                    OutlinedButton(onClick = { openedId = p.id }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("${p.brand} · ${p.model} · ${p.commands.size} commands")
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (vm.importText != null) ProfileForm("Import remote", null, category, vm::dismissImport) { name, cat, brandValue, modelValue, source, license ->
        vm.importProfile(name, cat, brandValue, modelValue, source, license)
    }
    edit?.let { p -> ProfileForm("Edit remote", p, p.category, { edit = null }) { name, cat, b, m, s, l ->
        vm.update(p.copy(name = name, category = cat, brand = b.ifBlank { "Unknown Brand" }, model = m.ifBlank { "Unknown Model" }, source = s, license = l)); edit = null
    } }
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${p.name}?") },
        text = { Text("This removes the imported profile and its saved remote from this phone.") },
        confirmButton = { TextButton(onClick = { vm.delete(p); openedId = null; deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
    if (confirmOff) AlertDialog(onDismissRequest = { confirmOff = false }, title = { Text("Send to ${offProfiles.size} devices?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            offProfiles.forEach { Text("${it.name}: ${it.offCommand()?.name}") }
            Text("Power Toggle can turn devices ON. Commands are sent sequentially; you must check whether each device is OFF.")
        } }, confirmButton = { TextButton(onClick = { confirmOff = false; vm.allOff(offProfiles) }) { Text("Send commands") } },
        dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } })
}

@Composable
private fun CategoryPicker(value: DeviceCategory, enabled: Boolean = true, onChange: (DeviceCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Category: ${value.displayName} ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeviceCategory.selectable.forEach { c -> DropdownMenuItem(text = { Text(c.displayName) }, onClick = { onChange(c); expanded = false }) }
        }
    }
}

@Composable
private fun ProfileForm(title: String, profile: RemoteProfile?, initialCategory: DeviceCategory, dismiss: () -> Unit,
    save: (String, DeviceCategory, String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile?.name ?: "") }
    var category by remember { mutableStateOf(initialCategory) }
    var brand by rememberSaveable { mutableStateOf(profile?.brand ?: "") }
    var model by rememberSaveable { mutableStateOf(profile?.model ?: "") }
    var source by rememberSaveable { mutableStateOf(profile?.source ?: "") }
    var license by rememberSaveable { mutableStateOf(profile?.license ?: "") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text("Remote name") }, singleLine = true)
            CategoryPicker(category) { category = it }
            OutlinedTextField(brand, { brand = it.take(100) }, label = { Text("Brand (blank = unknown)") }, singleLine = true)
            OutlinedTextField(model, { model = it.take(100) }, label = { Text("Model (blank = unknown)") }, singleLine = true)
            OutlinedTextField(source, { source = it.take(500) }, label = { Text("Source / attribution") })
            OutlinedTextField(license, { license = it.take(500) }, label = { Text("Source license, if known") })
            Text("Original file comments are retained locally. Import only files you are entitled to use. No network is required after import.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { save(name.trim(), category, brand.trim(), model.trim(), source.trim(), license.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
