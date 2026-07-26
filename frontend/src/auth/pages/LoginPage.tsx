import { AuthLayout } from './AuthLayout';
import { AuthForm } from './AuthForm';
import { useAuth } from '../useAuth';

export function LoginPage({ onRegister }: { onRegister: () => void }) {
  const auth = useAuth();
  return (
    <AuthLayout
      title="Boss raids, tiny capes, huge consequences."
      subtitle="Sign in to keep your raid pass warm while the first TitanCore arena wakes up."
    >
      <AuthForm
        mode="login"
        onSubmit={({ login, password }) => auth.login(login ?? '', password)}
        switchLabel="Need a banner? Register"
        switchAction={onRegister}
      />
    </AuthLayout>
  );
}
