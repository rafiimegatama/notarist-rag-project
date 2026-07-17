import { StatusBar } from 'expo-status-bar';
import * as NativeSplashScreen from 'expo-splash-screen';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PreferencesProvider } from './src/context/PreferencesContext';
import { ThemeProvider, useThemeMeta } from './src/context/ThemeContext';
import { AuthProvider } from './src/context/AuthContext';
import AppNavigator from './src/navigation/AppNavigator';
import NetworkBanner from './src/components/NetworkBanner';

// Keep the NATIVE splash up until our branded JS splash has painted its first frame (SplashScreen
// hides it on layout). Without this the native splash disappears the moment the JS bundle mounts,
// showing a blank frame before the first render. Must run in global scope, per expo-splash-screen.
NativeSplashScreen.preventAutoHideAsync().catch(() => {
  // Already hidden / unsupported (e.g. web) — the JS splash still covers the transition.
});

// StatusBar contrast follows the resolved theme.
function ThemedStatusBar() {
  const { scheme } = useThemeMeta();
  return <StatusBar style={scheme === 'light' ? 'dark' : 'light'} />;
}

// Provider order matters: Preferences → Theme (Theme reads themeMode) → Auth. SafeAreaProvider must
// wrap everything that uses insets. Auth flow itself is unchanged — only wrapped by new providers.
//
// NetworkBanner is a SIBLING of the navigator, not a wrapper around it: it is absolutely positioned
// and renders after AppNavigator, so it paints above every screen while adding no layout of its own.
// Navigation structure and screen layouts are untouched — nothing shifts when the banner appears.
export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <PreferencesProvider>
          <ThemeProvider>
            <AuthProvider>
              <AppNavigator />
              <NetworkBanner />
              <ThemedStatusBar />
            </AuthProvider>
          </ThemeProvider>
        </PreferencesProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
