package app.gamenative.companion

import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.gamenative.companion.rooms.BagsRoom
import app.gamenative.companion.rooms.CalendarRoom
import app.gamenative.companion.rooms.CharacterRoom
import app.gamenative.companion.rooms.ChatRoom
import app.gamenative.companion.rooms.GroupRoom
import app.gamenative.companion.rooms.HomeRoom
import app.gamenative.companion.rooms.MapRoom
import app.gamenative.companion.rooms.ProfessionsRoom
import app.gamenative.companion.rooms.PvpRoom
import app.gamenative.companion.rooms.QuestsRoom
import app.gamenative.companion.rooms.SocialRoom
import app.gamenative.companion.rooms.SpellsRoom
import app.gamenative.companion.rooms.TalentsRoom
import com.winlator.xserver.XServer

/**
 * The bottom screen as a WoW companion: the rooms of DS Forever's app, drawn in the PC UI's look, fed by the
 * DS Forever add-ons through the game's log ([CompanionLink]) and acting through their tap grid ([CompanionTap]).
 * It lives in the second-screen Presentation, which is not an Activity, so it brings its own lifecycle.
 */
class CompanionView(
    context: Context,
    xServer: XServer,
    private val onShowPad: () -> Unit,
) : FrameLayout(context), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        saved.performRestore(Bundle())
        registry.currentState = Lifecycle.State.CREATED
        CompanionTap.xServer = xServer
        CompanionLink.start(context)
        setViewTreeLifecycleOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        setViewTreeViewModelStoreOwner(this)
        addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { CompanionApp(onShowPad) }
            },
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.CREATED
        super.onDetachedFromWindow()
    }
}

/** A room: its tab label, the icon on its tab and what it draws. */
private data class Room(val label: String, val icon: String, val draw: @Composable (Snapshot) -> Unit)

private val ROOMS = listOf(
    Room("Home", "inv_misc_head_orc_01") { HomeRoom(it) },
    Room("Map", "inv_misc_map_01") { MapRoom(it) },
    Room("Quests", "inv_misc_book_08") { QuestsRoom(it) },
    Room("Bags", "inv_misc_bag_08") { BagsRoom(it) },
    Room("Character", "inv_chest_leather_01") { CharacterRoom(it) },
    Room("Spells", "inv_misc_book_09") { SpellsRoom(it) },
    Room("Talents", "ability_marksmanship") { TalentsRoom(it) },
    Room("Professions", "trade_blacksmithing") { ProfessionsRoom(it) },
    Room("Chat", "inv_letter_15") { ChatRoom(it) },
    Room("Social", "achievement_guildperk_everybodysfriend") { SocialRoom(it) },
    Room("Group", "inv_misc_groupneedmore") { GroupRoom(it) },
    Room("PvP", "achievement_bg_winwsg") { PvpRoom(it) },
    Room("Calendar", "inv_misc_note_06") { CalendarRoom(it) },
)

@Composable
private fun CompanionApp(onShowPad: () -> Unit) {
    val snap by CompanionLink.docs.collectAsState()
    var room by rememberSaveable { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Wow.Screen),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(end = 4.dp),
            ) {
                items(ROOMS.size) { i ->
                    Tab(ROOMS[i], selected = i == room) { room = i }
                }
            }
            Spacer(Modifier.width(4.dp))
            // back to the launcher's own button pad / trackpad / keyboard
            Box(
                Modifier
                    .daylight(band = false)
                    .clickable { onShowPad() }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) { Label("Pad", 13f, Wow.Gold) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(6.dp),
        ) {
            if (snap.version == 0L) Waiting() else ROOMS[room].draw(snap)
        }
    }
}

@Composable
private fun Tab(room: Room, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .daylight(band = selected, alpha = if (selected) 0.95f else 0.8f)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WowIcon(room.icon, 22.dp)
            Spacer(Modifier.width(5.dp))
            Column {
                Label(room.label, 13f, if (selected) Wow.Gold else Wow.Label)
                if (selected) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .width(28.dp)
                            .height(2.dp)
                            .background(Wow.Sweep),
                    )
                }
            }
        }
    }
}

@Composable
private fun Waiting() {
    Panel(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            WowIcon("inv_misc_pocketwatch_01", 48.dp)
            Spacer(Modifier.height(10.dp))
            Label("Waiting for the game…", 18f, Wow.Gold)
            Spacer(Modifier.height(6.dp))
            Label("The DS Forever add-ons send everything here once you're in the world.", 13f, Wow.Label, lines = 3)
        }
    }
}

/** A small "can't do that right now" line, for a tap the grid doesn't have. */
@Composable
fun Unavailable(text: String) {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Label(text, 13f, Wow.Dim, lines = 2)
    }
}

/** A Daylight-framed button. */
@Composable
fun WowButton(text: String, modifier: Modifier = Modifier, color: Color = Wow.Gold, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .daylight(band = false, alpha = if (enabled) 0.95f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Label(text, 14f, if (enabled) color else Wow.Dim) }
}

/** A row of small tabs inside a room (Character's pages, Talents' trees…). */
@Composable
fun SubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        items(titles.size) { i ->
            Box(
                Modifier
                    .daylight(band = i == selected, alpha = 0.9f)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Label(titles[i], 13f, if (i == selected) Wow.Gold else Wow.Label) }
        }
    }
}

/** The icon slot size most lists use. */
val ROW_ICON = 30.dp
