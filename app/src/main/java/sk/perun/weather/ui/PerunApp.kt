package sk.perun.weather.ui

import sk.perun.weather.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Tab(val route:String,val label:String,val icon:ImageVector) {
    Overview("overview",uiText(R.string.ui_teraz_6172392c),PerunIcons.Home),Forecast("forecast",uiText(R.string.ui_predpoved_20fdfd9d),PerunIcons.Chart),Maps("maps",uiText(R.string.ui_mapy_c9e02d67),PerunIcons.Map),Alerts("alerts",uiText(R.string.ui_vystrahy_d88f15ec),PerunIcons.Alert),More("more",uiText(R.string.ui_viac_66c91b9a),PerunIcons.More)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PerunApp(notificationRoute:String?=null,notificationLocation:String?=null,notificationToken:Long=0L,notificationAlert:String?=null) {
    val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route?.substringBefore('?');val current=if(route?.startsWith("more/")==true) Tab.More.route else route
    val context=LocalContext.current;val locations=remember {LocationState(LocationStore(context))};var sheet by remember {mutableStateOf(false)}
    var sourceEditor by remember {mutableStateOf(false)}
    val background=Color(0xFF323E4F);val text=Color(0xFFE8EAF0);val accent=Color(0xFF4FC3F7);val muted=Color(0xFFBBC4D2)
    // No preset place: until the first location is added, the app only offers adding one.
    if(locations.locations.isEmpty()) {
        if(sheet) LocationsSheet(locations) {sheet=false}
        Box(Modifier.fillMaxSize().background(background),contentAlignment=Alignment.Center) {Button(onClick={sheet=true}) {Icon(PerunIcons.Plus,null);Text(uiText(R.string.ui_pridat_lokalitu_70dce8af))}}
        return
    }
    // The warning of a notification, opened in the detail of the Alerts screen once it has loaded.
    var focusAlert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notificationToken) {
        val requested=notificationRoute ?: return@LaunchedEffect
        if(requested=="alerts") focusAlert=notificationAlert?.takeIf { it.isNotBlank() }
        val allowed=requested in setOf("overview","alerts","forecast","maps","more/air","more/air?mode=pollen","more/sun-moon") || requested.startsWith("forecast?chart=") || requested.startsWith("forecast?day=")
        if(!allowed) return@LaunchedEffect
        val index=locations.locations.indexOfFirst { BackgroundRefresh.locationKey(it)==notificationLocation }
        if(index>=0) locations.select(index)
        nav.navigate(requested) { launchSingleTop=true;popUpTo(Tab.Overview.route) }
    }
    // Warms the cache for the neighbouring saved locations, so that swiping to them does not wait for the network.
    LaunchedEffect(locations.current) {
        delay(1500)
        val all=locations.locations;val at=all.indexOf(locations.current);val store=LocationSourceStore(context)
        for(n in listOf(at+1,at-1)) { val l=all.getOrNull(n) ?: continue
            try { withContext(Dispatchers.IO) { val src=store.read(l);loadOverview(l,src.alertSource,src.weatherModel);loadOverviewExtra(l,src.weatherModel) } }
            catch(e:CancellationException) { throw e } catch(e:Exception) { }
        }
    }
    // A place saved while the lookup failed has no country and so gets no MeteoAlarm alerts. The lookup is repeated once per place and run of the app.
    val retried=remember { mutableSetOf<String>() }
    LaunchedEffect(locations.locations) {
        for(place in locations.locations.filter { it.countryCode.isBlank() }) {
            val key=BackgroundRefresh.locationKey(place)
            if(key in retried) continue
            val fresh=try { withContext(Dispatchers.IO) { reversePlace(place.latitude,place.longitude) } } catch(e:CancellationException) { throw e } catch(e:Exception) { null }
            retried.add(key)
            if(fresh!=null) locations.enrich(place,fresh)
            delay(1100)
        }
    }
    val sources=rememberLocationSourcePreferences(locations.current)
    CompositionLocalProvider(LocalLocationSourcePreferences provides sources) {
        if(sheet) LocationsSheet(locations) {sheet=false}
        if(sourceEditor) key(locationSourceKey(locations.current)) {LocationSourceEditorHost(locations.current) {sourceEditor=false}}
        Scaffold(containerColor=background,topBar={
            TopAppBar(navigationIcon={IconButton({sheet=true}) {Icon(PerunIcons.Menu,uiText(R.string.ui_lokality_144a4cba))}},title={Text(locations.current.name)},actions={IconButton(onClick={sourceEditor=true},modifier=Modifier.semantics {contentDescription=uiText(R.string.ui_upravit_zdroje_lokality_7fabf29d)}) {LocationSourcePencil()}},colors=TopAppBarDefaults.topAppBarColors(containerColor=background,titleContentColor=text,navigationIconContentColor=text,actionIconContentColor=text))
        },bottomBar={
            NavigationBar(containerColor=background,contentColor=text,tonalElevation=0.dp) {
                Tab.entries.forEach {tab->NavigationBarItem(selected=current==tab.route,onClick={nav.navigate(tab.route) {popUpTo(Tab.Overview.route);launchSingleTop=true}},icon={Icon(tab.icon,tab.label)},label={Text(tab.label)},colors=NavigationBarItemDefaults.colors(selectedIconColor=accent,selectedTextColor=accent,indicatorColor=Color.Transparent,unselectedIconColor=muted,unselectedTextColor=muted))}
            }
        }) {padding->
            NavHost(nav,startDestination=Tab.Overview.route,modifier=Modifier.padding(padding)) {
                composable("settings") {AppSettingsScreen {nav.popBackStack()}}
                composable("more/air?mode={mode}",arguments=listOf(navArgument("mode") {defaultValue=""})) {backStack->val mode=backStack.arguments?.getString("mode").orEmpty();key(locations.current,mode) {AirQualityScreen(locations.current,onBack={nav.popBackStack()},initialMode=mode)}}
                composable("more/sun-moon") {key(locations.current) {SunMoonScreen(locations.current,onBack={nav.popBackStack()})}}
                composable("more/marine?metric={metric}",arguments=listOf(navArgument("metric") {defaultValue=""})) {backStack->val metric=backStack.arguments?.getString("metric").orEmpty();key(locations.current,metric) {MarineScreen(locations.current,onBack={nav.popBackStack()},targetMetric=metric)}}
                // day is the ISO date that the Day by hours widget shows; the Forecast screen opens it expanded.
                composable("forecast?chart={chart}&day={day}",arguments=listOf(navArgument("chart") {defaultValue=""},navArgument("day") {defaultValue=""})) {backStack->val chart=backStack.arguments?.getString("chart").orEmpty();val day=backStack.arguments?.getString("day").orEmpty();key(locations.current,chart,day) {ForecastScreen(locations.current,targetGraph=chart,targetDay=day)}}
                Tab.entries.filter {it!=Tab.Forecast}.forEach {tab->composable(tab.route) {
                    if(tab==Tab.Overview) key(locations.current) {
                        val events=OverviewNavigation(openForecast={chart->if(chart=="marine") nav.navigate("more/marine") {launchSingleTop=true} else if(chart in setOf("temperature","uv","precipitation","probability","cloud","wind","pressure","humidity","visibility")) nav.navigate("forecast?chart=$chart") {launchSingleTop=true} else if(chart=="forecast") nav.navigate(Tab.Forecast.route) {launchSingleTop=true}},openAlerts={nav.navigate(Tab.Alerts.route) {launchSingleTop=true}},openSunMoon={nav.navigate("more/sun-moon") {launchSingleTop=true}},openMarine={metric->nav.navigate("more/marine?metric=$metric") {launchSingleTop=true}},openAir={mode->nav.navigate("more/air?mode=$mode") {launchSingleTop=true}})
                        CompositionLocalProvider(LocalOverviewNavigation provides events) {Box(Modifier.fillMaxSize().horizontalSwipe(Unit) {step->val all=locations.locations;val next=all.indexOf(locations.current)+step;if(next in all.indices) locations.select(next)}) {Overview(locations.current)}}
                    } else if(tab==Tab.Maps) key(locations.current) {MapsScreen(locations.current)}
                    else if(tab==Tab.Alerts) key(locations.current) {AlertsScreen(locations.current,focusAlert) { focusAlert=null }}
                    else if(tab==Tab.More) MoreScreen(onAir={nav.navigate("more/air") {launchSingleTop=true}},onSettings={nav.navigate("settings") {launchSingleTop=true}},onSunMoon={nav.navigate("more/sun-moon") {launchSingleTop=true}},onMarine={nav.navigate("more/marine") {launchSingleTop=true}},location=locations.current)
                }}
            }
        }
    }
}
