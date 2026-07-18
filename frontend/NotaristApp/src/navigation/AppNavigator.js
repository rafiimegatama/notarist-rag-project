import React from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';

import { useAuth } from '../context/AuthContext';
import { useTheme, useThemeMeta } from '../context/ThemeContext';
import useBootstrap from '../hooks/useBootstrap';
import { AppStateProviders } from '../state';
import { FEATURES } from '../constants/config';

import SplashScreen from '../screens/splash/SplashScreen';
import LoginScreen from '../screens/LoginScreen';
import RegisterScreen from '../screens/auth/RegisterScreen';
import DashboardScreen from '../screens/dashboard/DashboardScreen';
import CaseListScreen from '../screens/case/CaseListScreen';
import CaseDetailScreen from '../screens/case/CaseDetailScreen';
import BundleScreen from '../screens/bundle/BundleScreen';
import OcrReviewScreen from '../screens/ocr/OcrReviewScreen';
import VerificationScreen from '../screens/verification/VerificationScreen';
import SearchScreen from '../screens/search/SearchScreen';
import ReminderScreen from '../screens/reminder/ReminderScreen';
import ConversationsScreen from '../screens/conversation/ConversationsScreen';
import DocumentsScreen from '../screens/DocumentsScreen';
import AssistantScreen from '../screens/AssistantScreen';
import ProfileScreen from '../screens/profile/ProfileScreen';
import SettingsScreen from '../screens/settings/SettingsScreen';
import NotificationScreen from '../screens/notification/NotificationScreen';
import PlaygroundScreen from '../screens/dev/PlaygroundScreen';

const RootStack = createNativeStackNavigator();
const AuthStackNav = createNativeStackNavigator();
const AppStackNav = createNativeStackNavigator();
const Tab = createBottomTabNavigator();

// Bottom tabs follow the case-centric workflow: Dashboard, Cases, Search, Reminders, Assistant.
const TAB_ICONS = { Beranda: '🏠', Kasus: '📁', Cari: '🔍', Pengingat: '🔔', Asisten: '🤖' };
function TabIcon({ name }) {
  return <Text style={{ fontSize: 20 }}>{TAB_ICONS[name] ?? '•'}</Text>;
}

// Header actions on every tab: conversation history + profile. navigate bubbles to the parent AppStack.
//
// Each control carries an accessibilityRole + label: without them the emoji glyph WAS the accessible
// name, so a screen reader announced "speech balloon" with no hint of "Riwayat Percakapan" and no
// button semantics (verified in a real browser — RC acceptance). The emoji is marked decorative and
// the touchable owns the name.
function HeaderAction({ label, glyph, onPress }) {
  return (
    <TouchableOpacity
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={{ paddingHorizontal: 8, minHeight: 40, justifyContent: 'center' }}
    >
      <Text accessibilityElementsHidden importantForAccessibility="no" style={{ fontSize: 18 }}>{glyph}</Text>
    </TouchableOpacity>
  );
}

function HeaderActions({ navigation }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingRight: 4 }}>
      <HeaderAction label="Riwayat Percakapan" glyph="💬" onPress={() => navigation.navigate('Conversations')} />
      <HeaderAction label="Notifikasi" glyph="🔔" onPress={() => navigation.navigate('Notification')} />
      <HeaderAction label="Profil" glyph="👤" onPress={() => navigation.navigate('Profile')} />
    </View>
  );
}

function MainTabs() {
  const theme = useTheme();
  return (
    <Tab.Navigator
      screenOptions={({ route, navigation }) => ({
        tabBarIcon: () => <TabIcon name={route.name} />,
        tabBarStyle: { backgroundColor: theme.colors.surface, borderTopColor: theme.colors.border },
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: theme.colors.textFaint,
        headerStyle: { backgroundColor: theme.colors.surface },
        headerTintColor: theme.colors.text,
        headerShadowVisible: false,
        headerRight: () => <HeaderActions navigation={navigation} />,
      })}
    >
      <Tab.Screen name="Beranda" component={DashboardScreen} options={{ title: 'Dashboard' }} />
      <Tab.Screen name="Kasus" component={CaseListScreen} options={{ title: 'Case' }} />
      <Tab.Screen name="Cari" component={SearchScreen} options={{ title: 'Cari' }} />
      <Tab.Screen name="Pengingat" component={ReminderScreen} options={{ title: 'Pengingat' }} />
      <Tab.Screen name="Asisten" component={AssistantScreen} options={{ title: 'Asisten AI' }} />
    </Tab.Navigator>
  );
}

// Unauthenticated flow: Login (no header) + Register.
function AuthStack() {
  const theme = useTheme();
  return (
    <AuthStackNav.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: theme.colors.surface },
        headerTintColor: theme.colors.text,
        headerShadowVisible: false,
      }}
    >
      <AuthStackNav.Screen name="Login" component={LoginScreen} options={{ headerShown: false }} />
      <AuthStackNav.Screen name="Register" component={RegisterScreen} options={{ title: 'Daftar' }} />
    </AuthStackNav.Navigator>
  );
}

// Authenticated flow: tabs + the pushable workflow screens. Wrapped in the modular state providers so
// only signed-in users mount case/bundle/reminder/search/conversation/dashboard state.
function AppStack() {
  const theme = useTheme();
  return (
    <AppStateProviders>
      <AppStackNav.Navigator
        screenOptions={{
          headerStyle: { backgroundColor: theme.colors.surface },
          headerTintColor: theme.colors.text,
          headerShadowVisible: false,
        }}
      >
        <AppStackNav.Screen name="Main" component={MainTabs} options={{ headerShown: false }} />
        <AppStackNav.Screen name="CaseDetail" component={CaseDetailScreen} options={{ title: 'Detail Case' }} />
        <AppStackNav.Screen name="Bundle" component={BundleScreen} options={{ title: 'Bundle' }} />
        <AppStackNav.Screen name="OcrReview" component={OcrReviewScreen} options={{ title: 'OCR Review' }} />
        <AppStackNav.Screen name="Verification" component={VerificationScreen} options={{ title: 'Verifikasi' }} />
        <AppStackNav.Screen name="Conversations" component={ConversationsScreen} options={{ title: 'Riwayat Percakapan' }} />
        <AppStackNav.Screen name="Dokumen" component={DocumentsScreen} options={{ title: 'Dokumen' }} />
        <AppStackNav.Screen name="Profile" component={ProfileScreen} options={{ title: 'Profil' }} />
        <AppStackNav.Screen name="Settings" component={SettingsScreen} options={{ title: 'Pengaturan' }} />
        <AppStackNav.Screen name="Notification" component={NotificationScreen} options={{ title: 'Notifikasi' }} />
        {/* Developer-only: registered solely when the feature flag is on, so it never ships to users. */}
        {FEATURES.devPlayground ? (
          <AppStackNav.Screen name="Playground" component={PlaygroundScreen} options={{ title: 'Component Playground' }} />
        ) : null}
      </AppStackNav.Navigator>
    </AppStateProviders>
  );
}

export default function AppNavigator() {
  const { user } = useAuth();
  const { ready } = useBootstrap();
  const { theme, scheme } = useThemeMeta();

  // Branded splash until session check + preferences hydration + minimum time complete.
  if (!ready) return <SplashScreen />;

  const base = scheme === 'light' ? DefaultTheme : DarkTheme;
  const navTheme = {
    ...base,
    colors: {
      ...base.colors,
      primary: theme.colors.primary,
      background: theme.colors.background,
      card: theme.colors.surface,
      text: theme.colors.text,
      border: theme.colors.border,
      notification: theme.colors.badge,
    },
  };

  // Auth gating unchanged: presence of `user` selects the authenticated stack.
  return (
    <NavigationContainer theme={navTheme}>
      <RootStack.Navigator screenOptions={{ headerShown: false }}>
        {user ? (
          <RootStack.Screen name="App" component={AppStack} />
        ) : (
          <RootStack.Screen name="Auth" component={AuthStack} />
        )}
      </RootStack.Navigator>
    </NavigationContainer>
  );
}
