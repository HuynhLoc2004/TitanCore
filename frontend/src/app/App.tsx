import { AuthProvider } from '../auth/AuthProvider';
import { AppRoutes } from './AppRoutes';

export function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  );
}
