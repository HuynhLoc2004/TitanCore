import { AuthLayout } from './AuthLayout';
import { AuthForm } from './AuthForm';
import { useAuth } from '../useAuth';

export function RegisterPage({ onLogin }: { onLogin: () => void }) {
  const auth = useAuth();
  return (
    <AuthLayout
      title="Claim a name before the boss does."
      subtitle="Create your champion profile and get ready for the browser raid room."
    >
      <AuthForm
        mode="register"
        onSubmit={({ email, username, password }) => auth.register(email ?? '', username ?? '', password)}
        switchLabel="Already forged? Log in"
        switchAction={onLogin}
      />
    </AuthLayout>
  );
}
