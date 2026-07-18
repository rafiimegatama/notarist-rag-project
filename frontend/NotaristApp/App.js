import { StatusBar } from 'expo-status-bar';
import * as NativeSplashScreen from 'expo-splash-screen';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PreferencesProvider } from './src/context/PreferencesContext';
import { ThemeProvider, useThemeMeta } from './src/context/ThemeContext';
import { LoadingProvider } from './src/context/LoadingContext';
import { AuthProvider } from './src/context/AuthContext';
import AppNavigator from './src/navigation/AppNavigator';
import NetworkBanner from './src/components/NetworkBanner';
import GlobalLoadingOverlay from './src/components/GlobalLoadingOverlay';

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

// Provider order matters: Preferences → Theme (Theme reads themeMode) → Loading → Auth. SafeAreaProvider
// must wrap everything that uses insets. LoadingProvider sits INSIDE ThemeProvider (the overlay is
// themed) and OUTSIDE AuthProvider so signOut can run under a global loading task. Auth flow itself is
// unchanged — only wrapped by new providers.
//
// NetworkBanner and GlobalLoadingOverlay are SIBLINGS of the navigator, not wrappers: they render
// after AppNavigator, paint above every screen, and add no layout of their own. The loading overlay
// renders last so it sits above the network banner — a blocking wait outranks a connectivity strip.
// Both survive signOut swapping the navigator between the app and auth stacks, because they live
// above that swap.
export default function App() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <PreferencesProvider>
          <ThemeProvider>
            <LoadingProvider>
              <AuthProvider>
                <AppNavigator />
                <NetworkBanner />
                <GlobalLoadingOverlay />
                <ThemedStatusBar />
              </AuthProvider>
            </LoadingProvider>
          </ThemeProvider>
        </PreferencesProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
